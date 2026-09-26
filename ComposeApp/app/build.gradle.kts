import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 签名配置：从项目根 keystore.properties 读取（本地文件，不入库，见 .gitignore）。
// 文件缺失时 release 不签名（仍可构建 debug / 未签名 release）。
val keystoreProps = Properties().apply {
    // 项目根 = ComposeApp 的上级目录（class/）
    val root = rootProject.projectDir.parentFile
    val f = File(root, "keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val hasSigning = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.buguake.timetable"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.buguake.timetable"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.6"

        // 仅保留 arm64-v8a（已无原生库依赖，收窄以备将来）
        ndk {
            abiFilters += listOf("arm64-v8a")
        }    }

    if (hasSigning) {
        signingConfigs {
            create("release") {
                val rootDir = rootProject.projectDir.parentFile
                storeFile = File(rootDir, keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 与正式版共存（不覆盖用户数据），供 adb 驱动解析联调：run-as 可读私有日志
            applicationIdSuffix = ".debug"
            // 模拟器联调：x86_64 镜像可安装运行
            ndk {
                abiFilters += listOf("x86_64")
            }
        }
        release {
            // R8 代码压缩 + 资源收缩（性能方案 P0-1）：DEX 缩小、应用自身类可被 AOT 全量编译，
            // 冷启动 JIT 压力大幅下降；keep 规则见 proguard-rules.pro（WebView JS 桥等反射入口）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigning) {
                // 正式签名（密钥来自 keystore.properties，不在仓库）
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME：关于页/设置页统一读取，避免多处硬编码漂移
        buildConfig = true
    }

    lint {
        // lifecycle 2.8.x lint detector 与 Kotlin 2.1.21 K2 UAST 的已知崩溃
        // （NonNullableMutableLiveDataDetector IncompatibleClassChangeError），
        // 本应用不使用 LiveData，禁用该检查器即可通过 release 构建
        disable += "NullSafeMutableLiveData"
    }
}

ksp {
    // Room schema 导出：schema JSON 入库，配合 MigrationTestHelper 做迁移测试
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose + Material 3：material3 1.4.0（Expressive 组件/动效进入稳定线），
    // 显式指定版本，其余由 BOM 统一管理；1.4.0 不再传递依赖 material-icons，需显式引入
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // 教务网页导入：适配器仓库同步（HTTP 下载）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Room（KSP 编译期处理）
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // 单元测试
    testImplementation("junit:junit:4.13.2")
    // org.json 的 JVM 实现：本地单测跑桥 JSON 解析（android.jar 桩是 not mocked）
    testImplementation("org.json:json:20240303")
}
