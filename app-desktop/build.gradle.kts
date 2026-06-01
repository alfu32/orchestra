plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage-json"))
    implementation(project(":completion-core"))
    implementation(project(":compiler-naive-kotlin"))
}

application {
    mainClass.set("com.orchestra.app.MainKt")
}

val releaseNumber = rootProject.extra["orchestraReleaseNumber"] as String
val gitCommitId = rootProject.extra["orchestraGitCommitId"] as String
val gitTag = rootProject.extra["orchestraGitTag"] as String?
val buildDate = rootProject.extra["orchestraBuildDate"] as String

tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Builds a runnable fat jar in the root dist directory."
    dependsOn(project(":core").tasks.named("generateVersionSource"))
    dependsOn(configurations.runtimeClasspath)

    archiveBaseName.set("orchestra")
    archiveVersion.set(releaseNumber)
    archiveClassifier.set("")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("dist"))
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    doFirst {
        destinationDirectory.get().asFile.mkdirs()
    }

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Version"] = releaseNumber
        attributes["Git-Commit"] = gitCommitId
        attributes["Build-Date"] = buildDate
        gitTag?.let { attributes["Git-Tag"] = it }
    }

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.isFile && it.name.endsWith(".jar") }
            .map(::zipTree)
    })
}
