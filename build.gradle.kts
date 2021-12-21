plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.3.16"
  kotlin("plugin.spring") version "1.5.30"
  kotlin("plugin.jpa") version "1.5.30"
  id("io.gitlab.arturbosch.detekt").version("1.18.0-RC2")
  id("jacoco")
}
project.extensions.extraProperties["log4j2.version"] = "2.17.0"

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

val integrationTest = task<Test>("integrationTest") {
  description = "Integration tests"
  group = "verification"
  shouldRunAfter("test")
}

tasks.named<Test>("integrationTest") {
  useJUnitPlatform()
  filter {
    includeTestsMatching("*.integration.*")
  }
}

tasks.named<Test>("test") {
  filter {
    excludeTestsMatching("*.integration.*")
  }
}

tasks.named("check") {
  setDependsOn(
    dependsOn.filterNot {
      it is TaskProvider<*> && it.name == "detekt"
    }
  )
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  // Spring boot dependencies
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  // GOVUK Notify:
  implementation("uk.gov.service.notify:notifications-java-client:3.17.2-RELEASE")

  // Enable kotlin reflect
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")

  // Three Ten Date Calculations
  implementation("org.threeten:threeten-extra:1.7.0")

  // Database dependencies
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:postgresql:42.3.1")

  implementation("com.squareup.moshi:moshi-kotlin:1.12.0")
  implementation("io.arrow-kt:arrow-core:1.0.1")
  implementation("com.vladmihalcea:hibernate-types-52:2.14.0")

  // OpenAPI
  implementation("org.springdoc:springdoc-openapi-ui:1.5.12")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.5.12")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.12")

  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
  implementation("com.amazonaws:aws-java-sdk-sns:1.12.122")
  implementation("org.springframework.cloud:spring-cloud-aws-messaging:2.2.6.RELEASE")
  implementation("org.springframework:spring-jms")
  implementation("com.google.code.gson:gson:2.8.9")

  // Test dependencies
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.awaitility:awaitility-kotlin:4.1.1")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.28.0")
  testImplementation("io.swagger.parser.v3:swagger-parser-v2-converter:2.0.28")
  testImplementation("org.mockito:mockito-inline:4.1.0")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
  testImplementation("com.h2database:h2")
}
repositories {
  mavenCentral()
}

jacoco {
  // You may modify the Jacoco version here
  toolVersion = "0.8.7"
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "16"
    }
  }
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(16))
}
