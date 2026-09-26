# Flowt 发版流程

> 面向第一次接触本项目的开发者或 agent：**日常发版只看第 4 节的 checklist**，从上到下执行即可完成一次完整发版。
> 第 1 节是一次性环境说明 —— 新机器 clone 后必读，否则 release 打不出可安装的包。

## 0. 版本管理的分工

| 层 | 管什么 | 规则 |
|---|---|---|
| `versionCode`（app/build.gradle.kts） | 能否**覆盖安装** | Android 安装判断只认它：每次发版 +1，**永不回退** |
| `versionName` + changelog | 用户看到的版本号与更新说明 | 与 Git tag 严格一致（命名规则见第 3 节） |
| Git tag | "哪份代码对应哪次发版"的永久锚点 | 命名 = `v` + versionName（`v0.1.0` ↔ `0.1.0`），一个 tag 对应一次发布 |

分支策略：单人项目 master 单分支即可，不要为日常开发开 feature 分支。版本管理的核心是 **tag，不是分支**；分支只在试验性大重构、怕搞坏主线时临时使用，用完即删。

## 1. 已就绪能力（本机已配置；新机器按恢复清单补齐）

| 能力 | 载体 | 说明 |
|---|---|---|
| 签名密钥 | `flowt.jks`（项目根目录，alias=`flowt`，有效期 10000 天） | **不入库**。新机器 clone 后必须从私人备份恢复到项目根目录，否则 release 打不出可安装的包 |
| 签名密码 | `local.properties` 末尾的 `flowt.storePassword` / `flowt.keyPassword` | **不入库**。新机器需手动补写这两行 |
| 远程仓库 | https://github.com/NvM2478/Flowt （public） | remote 名 `origin`，已关联本地 master |
| 发布通道 | GitHub Releases（[已发布的版本](https://github.com/NvM2478/Flowt/releases)） | Release 附件（APK）对所有人可直接下载 |
| `gh` CLI | 已登录账号 NvM2478 | `gh release create` 用它创建 Release |

**新机器恢复清单**（clone 后必做）：

1. 确认项目根目录有 `flowt.jks` —— 没有就从你的私人备份（云盘等）恢复；
2. 打开 `local.properties`（Android Studio 打开项目会自动生成它，内含 `sdk.dir`），在文件末尾追加：

   ```properties
   flowt.storePassword=<生成 flowt.jks 时设置的口令>
   flowt.keyPassword=<同上>
   ```

## 2. 关键文件与机制

| 文件 | 发版时做什么 |
|---|---|
| `app/build.gradle.kts` | 顶部 `appVersionName` 是版本号的**唯一真源**：`versionName` 与 APK 文件名（`Flowt-X.Y.Z.apk`）自动跟随它；`versionCode` 每次发版 +1 |
| `app/src/main/java/com/flowt/app/ui/settings/AboutScreen.kt` | 顶部 `changelog` 列表（**倒序**，新条目加在头部）：关于页"更新日志"弹窗自动展示 |
| 产物 | `app/build/outputs/apk/release/Flowt-X.Y.Z.apk`（`androidComponents` 块自动命名） |

## 3. 版本号语义（X.Y.Z）

递增哪一位由你按本次改动的**影响量级**自行判断，下表只是常见示例，不必拘泥于字面分类：

| 位 | 含义（举例） |
|---|---|
| X | 量级最大的改动，比如：正式版本上线、大版本里程碑 |
| Y | 量级中等的改动，比如：新增功能、功能重构 |
| Z | 量级最小的改动，比如：修 bug、调 UI、小改逻辑 |

- **首位为 0 表示非正式版本** —— 0.x.y 阶段的任何变化都属于非正式发布；
- 递增高位时低位归零：0.1.5 修 bug → 0.1.6；新增功能 → 0.2.0；正式上线 → 1.0.0；
- `versionCode` 与此无关：每次发版无条件 +1，只用于覆盖安装判断；
- tag 一律 `v` 前缀 + 完整版本号（`vX.Y.Z`）。

## 4. 每次发版 checklist

### 4.1 决定并写入新版本号

按第 3 节的语义判断本次发版递增哪一位：

- 只有修 bug、调 UI、小改逻辑 → 递增 Z（如 0.1.0 → 0.1.1）
- 新增功能、重构功能 → 递增 Y，Z 归零（如 0.1.0 → 0.2.0）
- 正式版本上线等大型改动 → 递增 X，Y/Z 归零

写入 `app/build.gradle.kts`：

```kotlin
val appVersionName = "X.Y.Z"       // 上一步选定的版本号
...
versionCode = 2                    // 示例：上次是 1 —— 填"上次发版的值 + 1"，永不回退
versionName = appVersionName       // 引用变量，不用改
```

"上次发版的值"可以从上一版的 `app/build/outputs/apk/release/output-metadata.json` 或当时的 `build.gradle.kts` 里查。

### 4.2 写更新日志

`AboutScreen.kt` 的 `changelog` 列表**头部**插入新条目：

```kotlin
private val changelog = listOf(
    ChangelogEntry(
        version = "X.Y.Z",
        date = "YYYY-MM-DD",
        items = listOf("变更点一", "变更点二"),
    ),
    ChangelogEntry( /* 旧版本条目保持不动 */ ),
)
```

### 4.3 打包

```bash
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/Flowt-X.Y.Z.apk`

### 4.4 自测

把 APK 装到真机做一轮冒烟：记一笔账、切配色方案与明暗、看关于页版本号是否为本次版本。

### 4.5 提交并打标签

```bash
git add -A
git commit -m "chore: X.Y.Z 发版"
git tag -a vX.Y.Z -m "变更点一；变更点二"
git push origin master vX.Y.Z
```

（如果发版前还有未提交的功能改动，功能与发版号合并在一个 commit 也可以，tag 打在最终状态上即可。）

### 4.6 发布 GitHub Release

```bash
gh release create vX.Y.Z app/build/outputs/apk/release/Flowt-X.Y.Z.apk \
  --title "Flowt X.Y.Z" \
  --notes "变更点一；变更点二"
```

发布后把 Release 链接发给用户即可 —— 附件对所有人可直接下载，历史版本都在仓库的 Releases 页归档。

## 5. 验证签名（可选）

构建成功 ≠ 签名成功（密码为空时产物会是未签名包）。用 SDK 自带的 apksigner 验证：

```bash
"D:/app_code/Android/SDK/build-tools/36.0.0/apksigner.bat" verify --print-certs app/build/outputs/apk/release/Flowt-X.Y.Z.apk
```

出现 `Signer #1 certificate DN: CN=BrownSword, C=CN` 即签名有效。

**不要用 `jarsigner`** —— 它只认老式 v1（JAR 清单）签名，而 APK 用的是 v2/v3 签名方案，jarsigner 会误报"没有清单，jar 未签名"。

## 6. 常见问题

| 现象 | 原因与处理 |
|---|---|
| 对方安装提示"应用未安装" | 多数是 versionCode 没加大（覆盖安装要求新包 versionCode 更大），或对方设备上装过**其他签名**的同名包 → 只能卸载重装（数据会丢） |
| `assembleRelease` 构建报签名密码错误 | `local.properties` 里两行密码与生成 `flowt.jks` 时的口令不一致 → 核对修改 |
| 改了 `versionName` 但关于页版本没变 | 改的可能是别处 —— 唯一真源是 `app/build.gradle.kts` 顶部的 `appVersionName` |
| 丢失 `flowt.jks` 或忘记密码 | **无法补救**：老用户无法覆盖升级，只能卸载重装。因此 jks 与密码必须有异地备份 |
