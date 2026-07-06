import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pythontopia.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.pythontopia"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-alpha01"
        // 本地化：面向中国高校学生，仅打包简体中文资源（缩减 APK，声明主语言）。
        @Suppress("DEPRECATION")
        resConfigs("zh")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = libs.versions.jvmTarget.get()
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val hasReleaseKeystore = keystorePropsFile.exists()
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val props = Properties().apply {
                    keystorePropsFile.inputStream().use { load(it) }
                }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 有正式 keystore（keystore.properties，已 gitignore）则用之；
            // 否则回退 debug 签名，保证演示期 assembleRelease 也能产出可安装包。
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    sourceSets {
        named("main") {
            kotlin.srcDir("src/main/kotlin")
            // libgdx.so 已 vendored 到 src/main/jniLibs/<abi>/，详见 src/main/jniLibs/README.md。
        }
        named("test") {
            kotlin.srcDir("src/test/kotlin")
        }
        named("androidTest") {
            kotlin.srcDir("src/androidTest/kotlin")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        // 多模块都依赖 LibGDX，避免 META-INF 资源冲突
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
        )
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(project(":core:network"))
    implementation(project(":game"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:hub"))
    implementation(project(":feature:dialog"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:fields"))
    implementation(project(":feature:workshop"))
    implementation(project(":feature:arena"))
    implementation(project(":feature:mine"))
    implementation(project(":feature:retro"))
    implementation(project(":feature:path"))
    implementation(project(":feature:mainline"))
    implementation(project(":feature:classroom"))
    implementation(project(":feature:tutor"))
    implementation(project(":feature:library"))
    implementation(project(":feature:boss"))
    implementation(project(":feature:endless"))
    implementation(project(":feature:expedition"))
    implementation(project(":feature:lecture"))
    implementation(project(":feature:feynman"))
    implementation(project(":feature:problems"))
    implementation(project(":feature:board"))
    implementation(project(":feature:teacherreview"))
    implementation(project(":feature:about"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Baseline Profile 运行时安装（AGP 已在 release 编译 ART 配置；此依赖保证无 Play 云配置的设备也能落地）。
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.libgdx.backend.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.manifest)
}
