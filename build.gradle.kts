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

val orchestraGitCommitId = gitOutput("rev-parse", "--short=12", "HEAD") ?: "unknown"
val orchestraGitTag = gitOutput("describe", "--tags", "--abbrev=0")
val orchestraBuildDate = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
val orchestraReleaseNumber = providers.gradleProperty("release_number")
    .map(::normalizeSemver)
    .orElse(provider { orchestraGitTag?.let(::normalizeSemver) ?: "0.1.0" })
    .get()

extra["orchestraReleaseNumber"] = orchestraReleaseNumber
extra["orchestraGitCommitId"] = orchestraGitCommitId
extra["orchestraGitTag"] = orchestraGitTag
extra["orchestraBuildDate"] = orchestraBuildDate

subprojects {
    group = "com.orchestra"
    version = orchestraReleaseNumber

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
    description = "Builds the Orchestra desktop fat jar into the root dist directory."
    dependsOn(":app-desktop:fatJar")
}
