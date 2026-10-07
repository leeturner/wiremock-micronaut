plugins {
    alias(libs.plugins.micronaut.application)
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

application {
    mainClass = "example.Application"
}

micronaut {
    version(providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get()))
    testRuntime("junit5")
}

dependencies {
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(project(":"))
    testImplementation("org.assertj:assertj-core")
}
