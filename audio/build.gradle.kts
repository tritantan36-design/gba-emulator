plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

}
android {
    namespace = "dev.gbalite.audio"
    compileSdk = 36
    defaultConfig {
        minSdk = 26

        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }

    buildTypes { release { isMinifyEnabled = false } }

}
dependencies {

    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext)
    androidTestImplementation(libs.test.core)
}
