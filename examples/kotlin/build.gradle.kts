plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
}

repositories { mavenCentral() }

kotlin { jvmToolchain(25) }

application {
    mainClass = "example.ApplicationKt"
}

micronaut {
    version(providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get()))
    testRuntime("junit5")
}

dependencies {
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(project(":"))
    testImplementation("org.assertj:assertj-core")
}
