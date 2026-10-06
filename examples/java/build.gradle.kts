plugins {
    alias(libs.plugins.micronaut.library)
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

micronaut {
    version(providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get()))
    testRuntime("junit5")
}

dependencies {
    implementation("io.micronaut:micronaut-http-client-jdk")
    runtimeOnly("io.micronaut.serde:micronaut-serde-jackson")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(project(":"))
    testImplementation("org.assertj:assertj-core")
}
