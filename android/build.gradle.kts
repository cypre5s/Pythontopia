import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}

fun Project.configureDetektForKotlinModule() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension>("detekt") {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        source.setFrom(
            files(
                "src/main/kotlin",
                "src/test/kotlin",
                "src/androidTest/kotlin",
            ),
        )
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = rootProject.libs.versions.jvmTarget.get()
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
        exclude("**/build/**")
    }
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        configureDetektForKotlinModule()
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configureDetektForKotlinModule()
    }
}

allprojects {
    afterEvaluate {
        configurations.all {
            resolutionStrategy {
                cacheChangingModulesFor(0, "seconds")
            }
        }
    }
}
