// Native Gatling (Java DSL) load tests: config from application.conf + -D, the verdict from the simulation's assertions —
// `./gradlew gatlingRun` locally, any runner (CI, a scheduled job) only sets properties and reads the exit code.
plugins {
    java
    checkstyle
    id("io.gatling.gradle") version "3.16.0"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val lombok = "org.projectlombok:lombok:1.18.44"

dependencies {
    // the version Gatling 3.16.0 brings itself, declared so the DTO mapping does not lean on a transitive dependency
    gatlingImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    gatlingCompileOnly(lombok)
    gatlingAnnotationProcessor(lombok)
}

checkstyle {
    toolVersion = "10.12.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}

// Secrets reach a local run as environment variables, as in a pod: .env/local.env (KEY=value lines, read literally, never logged)
// is passed to the Gatling process; application.conf picks them up with ${?NAME}.
tasks.withType<io.gatling.gradle.GatlingRunTask>().configureEach {
    val localEnv = file(".env/local.env")
    if (localEnv.exists()) {
        environment = environment + localEnv.readLines()
            .filter { it.contains('=') && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().removeSurrounding("\"") }
    }
}
