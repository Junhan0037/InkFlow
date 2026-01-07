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
    // Spring WebFlux 타입 의존성을 직접 고정해 미들웨어 구현에 사용한다.
    val springFrameworkVersion = "6.2.2"

    api(project(":libs:common-observability"))
    api("org.springframework:spring-webflux:$springFrameworkVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
