plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.15.5"
  id("org.springdoc.openapi-gradle-plugin") version "1.8.0"
  kotlin("plugin.spring") version "1.9.23"
  kotlin("plugin.jpa") version "1.9.23"
  id("jacoco")
}

configurations {
  testImplementation {
    exclude(group = "org.junit.vintage")
    exclude(group = "logback-classic")
  }
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
  implementation("uk.gov.service.notify:notifications-java-client:5.0.0-RELEASE")

  // Enable kotlin reflect
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.23")

  // Three Ten Date Calculations
  implementation("org.threeten:threeten-extra:1.7.2")

  compileOnly("javax.servlet:javax.servlet-api:4.0.1")

  // Database dependencies
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:postgresql:42.7.3")

  // JWT
  implementation("io.jsonwebtoken:jjwt-api:0.12.5")
  runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
  runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

  implementation("io.arrow-kt:arrow-core:1.2.3")
  implementation("io.hypersistence:hypersistence-utils-hibernate-60:3.7.3")

  // OpenAPI
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0")

  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:2.1.2")
  implementation("org.springframework.cloud:spring-cloud-aws-messaging:2.2.6.RELEASE")
  implementation("org.springframework:spring-jms:6.1.5")
  implementation("com.google.code.gson:gson:2.10.1")

  // SQS
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:3.1.1")

  // Test dependencies
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.2.7")
  testImplementation("io.swagger.parser.v3:swagger-parser-v2-converter:2.1.21")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
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
  toolVersion = "0.8.11"
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "21"
    }
  }
}

dependencyCheck {
  suppressionFiles.add("$rootDir/dependencyCheck/suppression.xml")
}

openApi {
  outputDir.set(layout.buildDirectory.dir("docs"))
  outputFileName.set("openapi.json")
  customBootRun.args.set(listOf("--spring.profiles.active=dev,localstack,docs"))
}

afterEvaluate {
  tasks.named("forkedSpringBootRun") {
    dependsOn("inspectClassesForKotlinIC")
    notCompatibleWithConfigurationCache(
      "See https://github.com/springdoc/springdoc-openapi-gradle-plugin/issues/102",
    )
  }

  tasks.named("forkedSpringBootStop") {
    notCompatibleWithConfigurationCache(
      "See https://github.com/springdoc/springdoc-openapi-gradle-plugin/issues/102",
    )
  }
}
