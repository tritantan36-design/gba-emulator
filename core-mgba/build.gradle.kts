plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

}
android {
    namespace = "dev.gbalite.coremgba"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        // Keep the standalone JNI test APK aligned with the app's target SDK;
        // an old-target modal can otherwise cover the instrumentation window.
        targetSdk = 36

        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "dev.gbalite.mgba.ForegroundJniRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }

    buildTypes {
        debug { externalNativeBuild { cmake { arguments += "-DGBA_UBSAN=${if(providers.gradleProperty("gbaUbsan").orNull=="true") "ON" else "OFF"}" } } }
        release { isMinifyEnabled = false }
        if (providers.gradleProperty("apiCompatibility").orNull == "true") {
            create("compat") {
                initWith(getByName("debug"))
                matchingFallbacks += "debug"
                ndk { abiFilters.clear(); abiFilters += "x86_64" }
                externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Debug" } }
            }
        }
    }
    if (providers.gradleProperty("apiCompatibility").orNull == "true") {
        sourceSets.getByName("compat").java.srcDir("src/debug/java")
    }

    ndkVersion = "27.2.12479018"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }

}
dependencies {
    implementation(project(":core-api"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext)
    androidTestImplementation(libs.test.core)
}
