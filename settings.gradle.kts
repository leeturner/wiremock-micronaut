pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "wiremock-micronaut"

include("examples:java", "examples:kotlin")
