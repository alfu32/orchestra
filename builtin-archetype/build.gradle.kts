plugins {
    `java-library`
}

tasks.processResources {
    from(layout.projectDirectory.dir("src/threadworks/main")) {
        into("workflow-archetypes")
    }
}
