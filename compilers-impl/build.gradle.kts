plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":compiler-api"))
    implementation(project(":core"))
    implementation("io.pebbletemplates:pebble:4.1.2")
    testImplementation(project(":storage-json"))
    testImplementation(kotlin("test"))
}
