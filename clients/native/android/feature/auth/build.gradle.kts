plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Android-R4.1: @Serializable annotations on feature-local AuthDto require the
    // kotlinx.serialization compiler plugin (Retrofit converter relies on it).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pythontopia.feature.auth"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(libs.versions.jvmTarget.get().toInt())
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
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

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // Android-R4.5: BiometricPrompt requires androidx.fragment.app.FragmentActivity
    // (which is exactly what app/MainActivity already extends).
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    // Android-R4.1: feature-local DTOs use kotlinx.serialization-json runtime.
    implementation(libs.kotlinx.serialization.json)
    // Android-R4.2: EncryptedSharedPreferences + MasterKey for JWT persistence.
    implementation(libs.androidx.security.crypto)
    // Android-R4.5: BiometricPrompt API for token unlock after cold start.
    implementation(libs.androidx.biometric)
    // Android-R4.1: feature wraps Retrofit endpoints not yet present in :core:network;
    // we depend on the same Retrofit instance provided by :core:network.NetworkModule.
    implementation(libs.retrofit)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
