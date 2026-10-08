plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":input"))
    implementation(project(":core-api"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
}
