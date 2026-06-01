plugins {
    id("org.jetbrains.kotlin.jvm")
}

apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
}

val releaseNumber = rootProject.extra["orchestraReleaseNumber"] as String
val gitCommitId = rootProject.extra["orchestraGitCommitId"] as String
val gitTag = rootProject.extra["orchestraGitTag"] as String?
val buildDate = rootProject.extra["orchestraBuildDate"] as String
val versionSource = layout.projectDirectory.file("src/main/kotlin/com/orchestra/Version.kt")

val generateVersionSource = tasks.register("generateVersionSource") {
    inputs.property("releaseNumber", releaseNumber)
    inputs.property("gitCommitId", gitCommitId)
    inputs.property("gitTag", gitTag.orEmpty())
    inputs.property("buildDate", buildDate)
    outputs.file(versionSource)

    doLast {
        val gitTagLiteral = gitTag?.let { "\"$it\"" } ?: "null"
        val file = versionSource.asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.orchestra

            data class Version(
                val semver: String,
                val gitCommitId: String,
                val gitTag: String?,
                val buildDate: String,
            ) {
                companion object {
                    val CURRENT = Version(
                        semver = "$releaseNumber",
                        gitCommitId = "$gitCommitId",
                        gitTag = $gitTagLiteral,
                        buildDate = "$buildDate",
                    )
                }
            }
            """.trimIndent() + "\n",
        )
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateVersionSource)
}

tasks.named("clean") {
    doFirst {
        delete(versionSource)
    }
}
