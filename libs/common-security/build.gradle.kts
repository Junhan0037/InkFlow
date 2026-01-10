plugins {
    kotlin("jvm") version "1.9.25"
    id("java-library")
}

group = "com"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    val springFrameworkVersion = "6.2.2"
    val junitVersion = "5.12.2"
    val junitPlatformVersion = "1.12.2"
    api(project(":libs:common-observability"))
    api("org.springframework:spring-webflux:$springFrameworkVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
