plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":compiler-api"))
    implementation(project(":core"))
    testImplementation(project(":storage-json"))
    testImplementation(kotlin("test"))
}
