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
    val slf4jVersion = "2.0.16"
    val micrometerVersion = "1.13.5"
    val junitVersion = "5.12.2"
    val junitPlatformVersion = "1.12.2"
    val logbackVersion = "1.5.13"
    api("org.slf4j:slf4j-api:$slf4jVersion")
    api("io.micrometer:micrometer-core:$micrometerVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
