buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.2.0")
    }
}

val semverPattern = Regex("""\d+\.\d+\.\d+([-.+][0-9A-Za-z.-]+)?""")

fun gitOutput(vararg args: String): String? = try {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && output.isNotBlank()) output else null
} catch (_: Exception) {
    null
}

fun normalizeSemver(value: String): String =
    value.trim().removePrefix("v").also {
        if (!semverPattern.matches(it)) {
            throw org.gradle.api.GradleException("Invalid release number '$value'. Expected semantic version like x.y.z.")
        }
    }

val threadworkGitCommitId = gitOutput("rev-parse", "--short=12", "HEAD") ?: "unknown"
val threadworkGitTag = gitOutput("describe", "--tags", "--abbrev=0")
val threadworkBuildDate = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
val threadworkReleaseNumber = providers.gradleProperty("release_number")
    .map(::normalizeSemver)
    .orElse(provider { threadworkGitTag?.let(::normalizeSemver) ?: "0.1.0" })
    .get()

extra["threadworkReleaseNumber"] = threadworkReleaseNumber
extra["threadworkGitCommitId"] = threadworkGitCommitId
extra["threadworkGitTag"] = threadworkGitTag
extra["threadworkBuildDate"] = threadworkBuildDate

subprojects {
    group = "com.threadwork"
    version = threadworkReleaseNumber

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.register("fatJar") {
    group = "distribution"
    description = "Builds the Threadwork desktop fat jar into the root dist directory."
    dependsOn(":app-desktop:fatJar")
}
