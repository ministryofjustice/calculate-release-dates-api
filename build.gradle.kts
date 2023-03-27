plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.8.3"
  id("org.springdoc.openapi-gradle-plugin") version "1.6.0"
  kotlin("plugin.spring") version "1.8.10"
  kotlin("plugin.jpa") version "1.8.10"
  id("jacoco")
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

tasks.named("check") {
  setDependsOn(
    dependsOn.filterNot {
      it is TaskProvider<*> && it.name == "detekt"
    }
  )
}

val awsSdkVersion = "1.12.285"

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
  implementation("uk.gov.service.notify:notifications-java-client:3.19.1-RELEASE")

  // Enable kotlin reflect
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.20")

  // Three Ten Date Calculations
  implementation("org.threeten:threeten-extra:1.7.2")

  // Database dependencies
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:postgresql:42.5.1")

  implementation("io.arrow-kt:arrow-core:1.1.2")
  implementation("com.vladmihalcea:hibernate-types-52:2.21.1")

  // OpenAPI
  implementation("org.springdoc:springdoc-openapi-ui:1.6.15")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.15")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.15")

  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.1.2")
  implementation("org.springframework.cloud:spring-cloud-aws-messaging:2.2.6.RELEASE")
  implementation("org.springframework:spring-jms:5.3.26")
  implementation("com.google.code.gson:gson:2.10.1")

  // SQS
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.2.0")

  // AWS
  implementation("com.amazonaws:aws-java-sdk-s3:$awsSdkVersion")
  implementation("com.amazonaws:aws-java-sdk-cloudformation:$awsSdkVersion")
  implementation("com.amazonaws:aws-java-sdk-core:$awsSdkVersion")
  implementation("com.amazonaws:aws-java-sdk-ec2:$awsSdkVersion")
  implementation("com.amazonaws:aws-java-sdk-kms:$awsSdkVersion")
  implementation("com.amazonaws:aws-java-sdk-sns:$awsSdkVersion")
  implementation("com.amazonaws:aws-java-sdk-sqs:$awsSdkVersion")
  implementation("com.amazonaws:jmespath-java:$awsSdkVersion")

  // Test dependencies
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.36.1")
  testImplementation("io.swagger.parser.v3:swagger-parser-v2-converter:2.1.12")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
  testImplementation("com.h2database:h2")

  if (project.hasProperty("docs")) {
    implementation("com.h2database:h2")
  }
}
repositories {
  mavenCentral()
}

jacoco {
  // You may modify the Jacoco version here
  toolVersion = "0.8.8"
}

kotlin {
  jvmToolchain {
    this.languageVersion.set(JavaLanguageVersion.of("19"))
  }
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "19"
    }
  }
}

dependencyCheck {
  suppressionFiles.add("$rootDir/dependencyCheck/suppression.xml")
}

openApi {
  outputDir.set(file("$buildDir/docs"))
  outputFileName.set("openapi.json")
  customBootRun.args.set(listOf("--spring.profiles.active=dev,localstack,docs"))
}
