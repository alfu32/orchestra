plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":core"))
    api(project(":compiler-api"))
    testImplementation(project(":storage-json"))
    testImplementation(kotlin("test"))
}
