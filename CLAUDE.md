# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

Flowt 是纯本地记账 Android 应用（Kotlin + Jetpack Compose + Room），不联网、不需要账号。界面、注释、提交信息全部用中文。

## 常用命令

```bash
./gradlew assembleDebug        # 构建 debug 包
./gradlew installDebug         # 部署到已连接真机（本项目主要验证方式）
./gradlew testDebugUnitTest    # 单元测试（JVM）
./gradlew testDebugUnitTest --tests "com.flowt.app.SomeTest"   # 单个测试类
./gradlew connectedAndroidTest # 仪器测试（需真机）
./gradlew lint
```

目前 `app/src/test` 与 `app/src/androidTest` 下只有 Studio 模板生成的占位测试，没有真实测试套件。

依赖解析走阿里云镜像、Gradle 发行版走腾讯云镜像（`settings.gradle.kts` / `gradle/wrapper/gradle-wrapper.properties`），移除镜像在国内会直接超时。

## 架构

单 Activity（`MainActivity`）+ 全 Compose，**不引入 Navigation 库**，页面切换全是手写状态，集中在 [FlowtApp.kt](app/src/main/java/com/flowt/app/ui/FlowtApp.kt)：

- 三个 tab（流水 / 报表 / 设置）由 `HorizontalPager` 承载，切换即平移取景框
- 记账 / 编辑页是 Scaffold **之上**的圆形揭示覆盖层（`EntryRevealHost`），不是 tab —— 圆心由"被点的元素"决定（FAB 或某张流水卡片）
- 设置二级页同样渲染在 Scaffold 之外（`SettingsSubPageHost`）。在 Scaffold 内部做的覆盖层盖不住底部导航栏和 FAB，这是它被提升到 `FlowtApp` 层的原因

依赖注入是手写的：`AppViewModel` 直接 new 三个仓库，把 Room/DataStore 的 Flow 转成 `StateFlow` 给 Compose。指标随流水变化自动重算，没有也别加"数据已变更"通知机制。

### 数据层铁律（改之前必读）

- 金额一律**整数分（Long）**，禁用 Double/Float；时间一律 epoch 毫秒
- 分类树 = `parentId` 自关联 + **派生** `path` 字段。path 只由 [CategoryRepository.kt](app/src/main/java/com/flowt/app/data/CategoryRepository.kt) 生成，不接受用户输入；查子树靠 path 前缀 LIKE（调用方负责转义 `%` `_`）
- 分类改名 / 移动时，整棵子树的 path 在**同一事务内**重算
- 删除分类：其子树下任一流水被引用即拒绝，调用方须先 `moveTransactions` 或 `deleteTransactionsOf`（外键是 NO_ACTION）
- 流水只存 `categoryId`，不存分类路径字符串 —— 所以改分类名不用动历史流水
- `fallbackToDestructiveMigration()` 被**刻意禁用**：找不到迁移路径宁可崩溃，也不静默清空用户账目。改表结构 = 升 `FlowtDatabase.version` + 写迁移 + 依赖 `app/schemas/` 里的 schema JSON

### 三个注册表：加功能通常是往表里加一行

- `LedgerMetric.all`（[LedgerMetric.kt](app/src/main/java/com/flowt/app/metrics/LedgerMetric.kt)）：指标口径。加一项后首页卡片、设置页勾选、偏好存储自动跟上。`compute` 返回 `null` 表示"没有数据"（显示 `—`），与"支出 0 元"严格区分
- `PresetTheme.all` + `ThemeVariant`（[PresetTheme.kt](app/src/main/java/com/flowt/app/ui/theme/PresetTheme.kt)）：配色方案，变体 id 形如 `violet@light`；设置页配色列表自动多出两行
- `RoleKey`（[ColorRoles.kt](app/src/main/java/com/flowt/app/ui/theme/ColorRoles.kt)）：用户可自定义的 15 个颜色角色 → M3 色槽的映射。**一个角色可以写多个色槽，但两个角色绝不能写同一个色槽** —— 会互相覆盖，表现为"用户改了颜色却没生效"（现有代码为规避这点，指标卡用 `tertiaryContainer` 而非已被占用的 `primaryContainer`）

账单文件的导入 / 导出不在注册表里，但同样是"只动一层"的结构（格式层 `TableReader` / 字段层同义词表 / 来源模式 `ImportMode`）。改动或扩展这块之前先读 [docs/data-management.md](docs/data-management.md) —— 那里记了完整设计、扩展步骤，以及一批踩过的坑（比如判重与落库必须共用同一套映射口径）。

### 其他约定

- [Transitions.kt](app/src/main/java/com/flowt/app/ui/Transitions.kt) 是全局动画规格的**唯一调节点**：主 tab 平移与二级页滑入滑出必须共用，否则快慢不一致肉眼可见
- `expenseColor()`（[Color.kt](app/src/main/java/com/flowt/app/ui/theme/Color.kt)）是**不跟随主题**的语义色（支出永远暖红），刻意做成纯函数而非 `@Composable`，以便非 Composable 处调用
- 界面偏好存 DataStore（`PrefsRepository`）而**不存 Room**：加一个开关不该触发 schema 迁移
- 主题在 Compose 树最顶层应用（`MainActivity` 里 `FlowtTheme` 包住 `FlowtApp`），改配色无需重启即可生效

## 构建期脚注

- `gradle.properties` 的 `android.disallowKotlinSourceSets=false` 是 KSP 2.2.10 与 AGP 9 内置 Kotlin 的过渡兼容开关，删掉会让 Clean/Rebuild 在配置阶段失败（文件里有 TODO 说明何时移除）
- `settings.gradle.kts` 的 `gradlePluginPortal()` 不能删（foojay-resolver 依赖它）
- `targetSdk` 与真机 HyperOS 3（Android 16 / API 36）对齐，缩小行为变更触发面
