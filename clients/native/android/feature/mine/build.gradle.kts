plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pythontopia.feature.mine"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = libs.versions.jvmTarget.get()
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Retrofit 注解（@POST / @GET / @Path / @Body）在 MineApi 中直接出现，
    // 所以必须显式 implementation；:core:network 用 implementation 引入 Retrofit
    // 不会传递到这里。Hilt 的 Retrofit @Singleton 已经在 :core:network 中提供，
    // 这里只要在编译期能看到 Retrofit 类型即可。
    implementation(libs.retrofit)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // MineRepositoryTest 用 MockWebServer + 真 Retrofit + kotlinx-serialization
    // 跑端到端 HTTP 验证，避免 mock 掉 api interface 错过 @POST/@GET 注解错配。
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.converter.kotlinx.serialization)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
