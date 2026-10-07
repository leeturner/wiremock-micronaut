plugins {
    `java-library`
    alias(libs.plugins.spotless)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.micronaut.library) apply false
    alias(libs.plugins.micronaut.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.ksp) apply false
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

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("io.github.leeturner", "wiremock-micronaut", version.toString())
    pom {
        name = "WireMock Micronaut"
        description = "WireMock integration for Micronaut tests"
        url = "https://github.com/leeturner/wiremock-micronaut"
        inceptionYear = "2026"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "leeturner"
                name = "Lee Turner"
                url = "https://leeturner.me"
            }
        }
        scm {
            url = "https://github.com/leeturner/wiremock-micronaut"
            connection = "scm:git:git://github.com/leeturner/wiremock-micronaut.git"
            developerConnection = "scm:git:ssh://git@github.com/leeturner/wiremock-micronaut.git"
        }
    }
}
