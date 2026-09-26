import java.util.Properties

// 签名密码从 local.properties 读取（该文件已被 .gitignore 排除，不会进仓库）；
// 两个密码属性缺省为空串时，release 包会以未签名形式产出、无法安装 —— 记得填写
val keystoreProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// 发版时改这里，versionName 与 APK 文件名会一起更新
val appVersionName = "0.1.1"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Room 编译期把 schema 导出为 JSON：迁移测试的依据，也是"以后改表结构不丢数据"的前提
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.flowt.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.flowt.app"
        minSdk = 26
        // 与真机 HyperOS 3 (Android 16 / API 36) 对齐，缩小行为变更触发面
        targetSdk = 36
        versionCode = 2
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("flowt.jks")
            storePassword = keystoreProperties.getProperty("flowt.storePassword") ?: ""
            keyAlias = "flowt"
            keyPassword = keystoreProperties.getProperty("flowt.keyPassword") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// 产物自动命名：release/debug 都输出为 Flowt-<版本号>.apk，发出去的文件自带版本可区分新旧
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("Flowt-$appVersionName.apk")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}