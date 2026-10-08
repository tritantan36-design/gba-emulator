plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

}
android {
    namespace = "dev.gbalite.renderer"
    compileSdk = 36
    defaultConfig {
        minSdk = 26

        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    buildFeatures { buildConfig = true }

    buildTypes { release { isMinifyEnabled = false } }

}
dependencies {
    implementation(project(":core-api"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext)
    androidTestImplementation(libs.test.core)
}
