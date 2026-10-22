// Native Gatling (Java DSL) load tests: config from application.conf + -D, the verdict from the simulation's assertions —
// `./gradlew gatlingRun` locally, any runner (CI, a scheduled job) only sets properties and reads the exit code.
plugins {
    java
    checkstyle
    id("io.gatling.gradle") version "3.16.0"
    // gRPC stubs from src/gatling/proto (the grpcdemo example) — generated into the gatling source set
    id("com.google.protobuf") version "0.9.4"
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
val grpc = "1.68.0"
val protobufVersion = "4.28.2"

dependencies {
    // the version Gatling 3.16.0 brings itself, declared so the DTO mapping does not lean on a transitive dependency
    gatlingImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    gatlingImplementation("io.grpc:grpc-netty-shaded:$grpc")
    gatlingImplementation("io.grpc:grpc-protobuf:$grpc")
    gatlingImplementation("io.grpc:grpc-stub:$grpc")
    gatlingImplementation("com.google.protobuf:protobuf-java:$protobufVersion")
    // the @javax.annotation.Generated on protoc-gen-grpc-java's stubs (not in the JDK since 11)
    gatlingCompileOnly("javax.annotation:javax.annotation-api:1.3.2")
    gatlingCompileOnly(lombok)
    gatlingAnnotationProcessor(lombok)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpc"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

checkstyle {
    toolVersion = "10.12.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}

// Secrets reach a local run as environment variables, as in a pod: .env/local.env (KEY=value lines, read literally, never logged)
// is passed to the Gatling process; application.conf picks them up with ${?NAME}.
// The run's parameters (-Dprofile=load -Drate=2 …, -D<env>.<path>=… config overrides) are passed on to the Gatling process; the
// simulation is named by its class: -Dsimulation=McpSimulation, found under **/simulations (or --simulation <fully qualified name>).
val runParameters = listOf(
    "env", "profile", "weights", "users", "rate", "rampUp", "steadyState", "step", "steps", "spikeFactor", "spike",
    "callsPerSession", "pauseMs", "feedSize", "abortFailedPercent", "maxFailedPercent", "maxCallP95Ms", "maxInitializeP95Ms",
    "maxProtocolP95Ms",
)

tasks.withType<io.gatling.gradle.GatlingRunTask>().configureEach {
    val forwarded = System.getProperties().stringPropertyNames()
        .filter { it in runParameters || it.startsWith("local.") || it.startsWith("dev.") }
        .associateWith { System.getProperty(it) }
    systemProperties = (systemProperties ?: emptyMap<String, Any>()) + forwarded
    System.getProperty("simulation")?.let { simulation ->
        val sources = file("src/gatling/java")
        val source = fileTree(sources).matching { include("**/simulations/$simulation.java") }.files.singleOrNull()
            ?: throw GradleException("No simulation $simulation under src/gatling/java/**/simulations")
        simulationClassName = source.relativeTo(sources).path.removeSuffix(".java").replace('/', '.')
    }
    val localEnv = file(".env/local.env")
    if (localEnv.exists()) {
        environment = environment + localEnv.readLines()
            .filter { it.contains('=') && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().removeSurrounding("\"") }
    }
}

// generated gRPC stubs are not ours to style
tasks.withType<Checkstyle>().configureEach {
    exclude("**/proto/**")
}
