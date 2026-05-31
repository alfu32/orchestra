# Orchestra

Kotlin implementation of the Orchestra/InFlow rewrite described in `spec/`.

Current implementation is the first functional backend slice:

- shared recursive node model
- in-memory document repository
- JSON save/load
- document validation
- metadata-aware completion service
- compiler API
- naive Kotlin/JVM project generator
- small CLI entry point in `app-desktop`

## Commands

Use a workspace-local Gradle cache:

```bash
GRADLE_USER_HOME=.gradle-user gradle test
GRADLE_USER_HOME=.gradle-user gradle :app-desktop:run --args='new build/sample.inflow.json'
GRADLE_USER_HOME=.gradle-user gradle :app-desktop:run --args='validate build/sample.inflow.json'
GRADLE_USER_HOME=.gradle-user gradle :app-desktop:run --args='compile build/sample.inflow.json build/generated-sample'
```

The Compose desktop shell and CodeMirror adapter are not implemented yet.
