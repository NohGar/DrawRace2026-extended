plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.1.0"
}

group = "backend"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

spotless {
    java {
        target("src/**/*.java")

        palantirJavaFormat()   // 4칸 들여쓰기 기반의 깔끔한 포맷팅
        removeUnusedImports()  // 사용하지 않는 import 자동 제거
        trimTrailingWhitespace() // 줄 끝 불필요한 공백 제거
        formatAnnotations()    // 어노테이션 배치 최적화
        endWithNewline()       // 파일 끝 개행 추가 (POSIX 표준)

        // Import 순서 정렬: 팀원 간의 Git 충돌 방지 핵심 설정
        importOrder(
            "java",
            "javax",
            "jakarta",
            "org",
            "com",
            "backend.drawrace",
            ""
        )
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")

    runtimeOnly("com.h2database:h2")

    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.codemonstur:embedded-redis:1.4.3")

    // JWT (jjwt)
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")


    // PyTorch
    implementation(platform("ai.djl:bom:0.26.0"))
    implementation("ai.djl:api")
    implementation("ai.djl.pytorch:pytorch-engine")
    runtimeOnly("ai.djl.pytorch:pytorch-native-auto")
    implementation("ai.djl.huggingface:tokenizers")

    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3:3.1.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}