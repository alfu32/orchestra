plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":core"))
    testImplementation(project(":storage-json"))
    testImplementation(kotlin("test"))
}
