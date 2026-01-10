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
    val grpcVersion = "1.64.0"
    val slf4jVersion = "2.0.16"
    val springFrameworkVersion = "6.2.2"
    val junitVersion = "5.12.2"
    val junitPlatformVersion = "1.12.2"
    api("io.grpc:grpc-api:$grpcVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")
    api("org.springframework:spring-context:$springFrameworkVersion")
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
