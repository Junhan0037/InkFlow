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
    // 버전 명시로 공통 관측성 의존성을 고정한다.
    val slf4jVersion = "2.0.16"
    val micrometerVersion = "1.13.5"

    // 로깅 MDC 및 공통 태그 정의에 필요한 최소 의존성만 제공한다.
    api("org.slf4j:slf4j-api:$slf4jVersion")
    api("io.micrometer:micrometer-core:$micrometerVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
