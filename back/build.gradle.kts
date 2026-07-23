plugins {
    java
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    kotlin("plugin.jpa") version "2.1.21"
    kotlin("plugin.lombok") version "2.1.21"
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "back"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0") // bucket4j
    implementation("com.github.ben-manes.caffeine:caffeine") // caffeine
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    // security
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    // JJWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    // Redisson (Sentinel, Delayed Queue, RScript 통합 클라이언트) - Spring Boot 4.0 호환 버전
    implementation("org.redisson:redisson-spring-boot-starter:4.6.1")
    // OAuth2
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.github.codemonstur:embedded-redis:1.4.3")
    //WebSocket
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
