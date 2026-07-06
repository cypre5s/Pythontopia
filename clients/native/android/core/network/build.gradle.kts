plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // 仅注册可选任务 openApiGenerate；不挂接 sourceSets、不参与常规 build（见文件末尾）。
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "com.pythontopia.core.network"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        // debug 默认指向公网 ECS 后端（明文，network_security_config 白名单放行）；
        // 模拟器/局域网联调可经 BaseUrlProvider 运行时覆盖为 10.0.2.2 等地址。
        buildConfigField("String", "BASE_URL", "\"http://47.120.29.62:18080/\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            // 生产走 HTTPS；上架前替换为真实域名。
            buildConfigField("String", "BASE_URL", "\"https://api.pythontopia.example/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(libs.versions.jvmTarget.get().toInt())
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 后端地址运行时覆盖的持久层（BaseUrlProvider/NetworkHostStore）。
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}

// ── OpenAPI 客户端代码生成（可选、未接入常规构建） ─────────────────────────────
// `org.openapi.generator` 插件会注册一个独立任务 `openApiGenerate`。这里只做配置，
// **刻意不**把生成目录加入 sourceSets，也**不**让任何 compile/assemble/test 依赖它，
// 以保证其它 agent 的常规构建保持为绿。手动触发：
//   ./gradlew :core:network:openApiGenerate
// 刷新 spec 的完整流程见 clients/native/android/openapi/README.md。
openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set("${rootProject.projectDir}/openapi/pythontopia-api.json")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    packageName.set("com.pythontopia.core.network.generated")
    apiPackage.set("com.pythontopia.core.network.generated.api")
    modelPackage.set("com.pythontopia.core.network.generated.model")
    // 与手写 DTO/Api 的运行时保持一致：kotlinx-serialization + 协程 + kotlinx-datetime。
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "kotlinx-datetime",
        ),
    )
}
