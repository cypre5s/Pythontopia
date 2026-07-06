plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pythontopia.webview"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pythontopia.webview"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        getByName("release") {
            // 外壳无业务代码，关闭混淆以避免误伤 WebView 回调；
            // 无正式 keystore 时回退 debug 签名，保证演示期可直接安装。
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        named("main") {
            kotlin.srcDir("src/main/kotlin")
        }
    }
}

dependencies {
    // 纯框架 WebView 外壳：不引入任何三方/androidx 依赖，保持构建轻量。
}
