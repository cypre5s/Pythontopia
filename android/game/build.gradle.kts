plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pythontopia.game"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.libgdx.core)
    implementation(libs.libgdx.backend.android)
    // LibGDX native .so 在 :app 模块通过 `copyAndroidNatives` 自定义 Gradle 任务
    // 从 gdx-platform classifier JAR 中解包到 :app/src/main/jniLibs/<abi>/libgdx.so，
    // 这样运行时 System.loadLibrary("gdx") 才能找到。详见 :app/build.gradle.kts。

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.startup)
    // gdx-backend-android 1.13.1 的 AndroidFragmentApplication 继承 androidx.fragment.app.Fragment，
    // 需要 fragment-ktx 把它的超类放到 :game 模块的编译期 classpath 上。
    api(libs.androidx.fragment.ktx)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
