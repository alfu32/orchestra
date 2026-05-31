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
