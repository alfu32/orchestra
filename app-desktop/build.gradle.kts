plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage-json"))
    implementation(project(":completion-core"))
    implementation(project(":compilers-impl"))
    implementation(project(":builtin-archetype"))
    implementation(project(":assets"))
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation(files(rootProject.file("lib/tinycc-embed.jar")))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.threadwork.app.MainKt")
}

val releaseNumber = rootProject.extra["threadworkReleaseNumber"] as String
val gitCommitId = rootProject.extra["threadworkGitCommitId"] as String
val gitTag = rootProject.extra["threadworkGitTag"] as String?
val buildDate = rootProject.extra["threadworkBuildDate"] as String

tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Builds a runnable fat jar in the root dist directory."
    dependsOn(project(":core").tasks.named("generateVersionSource"))
    dependsOn(configurations.runtimeClasspath)

    archiveBaseName.set("threadwork")
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
