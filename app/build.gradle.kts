plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.gbalite.app"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        applicationId = "dev.gbalite.app"
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0"

        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    buildFeatures { compose = true }
    buildTypes { release { isMinifyEnabled = false } }

}
dependencies {
    implementation(project(":feature-player"))
    implementation(project(":emulator-session"))
    implementation(project(":core-api"))
    implementation(project(":core-mgba"))
    implementation(project(":data"))
    implementation(project(":storage"))
    implementation(project(":input"))
    implementation(project(":renderer"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
}

tasks.register("exportRuntimeDependencies") {
    val report = layout.buildDirectory.file("reports/runtime-dependencies.txt")
    outputs.file(report)
    doLast {
        val coordinates = configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult
            .allComponents.mapNotNull { component ->
                val id = component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
                id?.let { "${it.group}:${it.module}:${it.version}" }
            }.distinct().sorted()
        report.get().asFile.apply { parentFile.mkdirs(); writeText(coordinates.joinToString("\n", postfix = "\n")) }
    }
}
