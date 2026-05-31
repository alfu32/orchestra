plugins {
    id("org.jetbrains.kotlin.jvm")
}

apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

dependencies {
    api(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
}
