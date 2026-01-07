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
    // gRPC 공통 인터셉터 구현에 필요한 최소 의존성을 고정한다.
    val grpcVersion = "1.64.0"

    api(project(":libs:common-observability"))
    api("io.grpc:grpc-api:$grpcVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
