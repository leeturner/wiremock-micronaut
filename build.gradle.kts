plugins {
    `java-library`
    alias(libs.plugins.spotless)
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

val micronautVersion: String =
    providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get())
val micronautPlatform = "io.micronaut.platform:micronaut-platform:$micronautVersion"

dependencies {
    api(libs.wiremock.standalone)

    compileOnly(platform(micronautPlatform))
    compileOnly("io.micronaut:micronaut-inject")
    compileOnly("io.micronaut.test:micronaut-test-junit5")
    compileOnly("org.junit.jupiter:junit-jupiter-api")
    compileOnly("org.slf4j:slf4j-api")
    compileOnly("jakarta.inject:jakarta.inject-api")
    compileOnly(libs.jspecify)

    annotationProcessor(platform(micronautPlatform))
    annotationProcessor("io.micronaut:micronaut-inject-java")

    testImplementation(platform(micronautPlatform))
    testImplementation("io.micronaut:micronaut-inject")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-testkit")
    testImplementation("org.assertj:assertj-core")
    testCompileOnly(libs.jspecify)
    testAnnotationProcessor(platform(micronautPlatform))
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.test {
    useJUnitPlatform()
    exclude("**/fixtures/**")
}

spotless {
    java {
        target("src/**/*.java", "examples/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get())
    }
}
