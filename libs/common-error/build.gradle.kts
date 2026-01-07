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
    // 에러 매핑과 응답 표준화를 위해 필요한 최소 의존성을 고정한다.
    val grpcVersion = "1.64.0"
    val slf4jVersion = "2.0.16"
    val springFrameworkVersion = "6.2.2"

    api("io.grpc:grpc-api:$grpcVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")
    api("org.springframework:spring-context:$springFrameworkVersion")
    api("org.springframework:spring-webflux:$springFrameworkVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
