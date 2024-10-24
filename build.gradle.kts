plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "6.0.8"
  id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
  kotlin("plugin.spring") version "2.0.21"
  kotlin("plugin.jpa") version "2.0.21"
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
  implementation("uk.gov.service.notify:notifications-java-client:5.2.1-RELEASE")

  // Enable kotlin reflect
  implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")

  // Three Ten Date Calculations
  implementation("org.threeten:threeten-extra:1.8.0")

  compileOnly("javax.servlet:javax.servlet-api:4.0.1")

  // Database dependencies
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql:42.7.4")

  // JWT
  implementation("io.jsonwebtoken:jjwt-api:0.12.6")
  runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
  runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

  implementation("io.arrow-kt:arrow-core:1.2.4")
  implementation("io.hypersistence:hypersistence-utils-hibernate-60:3.8.3")

  // OpenAPI
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:2.1.3")
  implementation("io.awspring.cloud:spring-cloud-aws-messaging:2.4.4")
  implementation("org.springframework:spring-jms:6.1.14")
  implementation("com.google.code.gson:gson:2.11.0")

  // SQS
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.0.2")

  // Test dependencies
  testImplementation("org.wiremock:wiremock-standalone:3.9.2")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.4.1")
  testImplementation("io.swagger.parser.v3:swagger-parser-v2-converter:2.1.23")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
  testImplementation("com.h2database:h2")
  testImplementation("io.github.hakky54:logcaptor:2.9.3")

  if (project.hasProperty("docs")) {
    implementation("com.h2database:h2")
  }
}
repositories {
  mavenCentral()
}

jacoco {
  // You may modify the Jacoco version here
  toolVersion = "0.8.12"
}

tasks.jacocoTestCoverageVerification {
  violationRules {
    rule {
      limit {
        minimum = "0.8".toBigDecimal()
      }
    }
  }
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
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
