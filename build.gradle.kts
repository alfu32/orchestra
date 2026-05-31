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

subprojects {
    group = "com.orchestra"
    version = "0.1.0"

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
