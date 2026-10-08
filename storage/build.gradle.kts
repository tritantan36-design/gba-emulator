plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core-api"))
    implementation(libs.gson)
    testImplementation(libs.junit)
}
if(providers.gradleProperty("phase7a").orNull=="true") {
    tasks.test {
        filter {
            // Phase 7A runs ordinary QA only. Retain the original fixtures and
            // default full suite for an explicitly authorized Phase 7B run.
            listOf("traversalAndAbsoluteDriveAndBackslashRejected",
                "nestedArchiveAndUnknownExtraRejected","hugeDeclaredSizesAndRatioRejected",
                "tooManyAndLongPathsRejected","corruptDirectoryTruncationAndEncryptionRejected",
                "invalidUtf8DuplicateCanonicalNamesAndCrcRejected").forEach {
                excludeTestsMatching("dev.gbalite.storage.RomImportTest.$it")
            }
            excludeTestsMatching("dev.gbalite.storage.Phase7ImportTest.malformed*")
        }
    }
}
tasks.register<JavaExec>("phase7ZipFuzz") {
    dependsOn(tasks.testClasses)
    classpath=sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.gbalite.storage.Phase7ZipFuzz")
    args(providers.gradleProperty("phase7FuzzSeconds").orElse("60").get())
}
