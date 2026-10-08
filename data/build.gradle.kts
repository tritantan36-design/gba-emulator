plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android {
    namespace = "dev.gbalite.data"; compileSdk = 36
    defaultConfig { minSdk = 26; javaCompileOptions { annotationProcessorOptions { arguments["room.schemaLocation"] = "$projectDir/schemas" } } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":core-api")); implementation(project(":storage"))
    implementation(libs.room.runtime); annotationProcessor(libs.room.compiler)
    implementation(libs.datastore.core)
    implementation(libs.coroutines.core)
}
