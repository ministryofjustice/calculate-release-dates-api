import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.0.3"
  id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
  kotlin("plugin.spring") version "2.3.10"
  kotlin("plugin.jpa") version "2.3.10"
  id("jacoco")
  id("org.openapi.generator") version "7.19.0"
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
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-flyway")

  // MoJ libraries
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.0.0")

  // GOVUK Notify:
  implementation("uk.gov.service.notify:notifications-java-client:6.0.0-RELEASE")

  // Enable kotlin reflect
  implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.10")

  // Three Ten Date Calculations
  implementation("org.threeten:threeten-extra:1.8.0")

  compileOnly("javax.servlet:javax.servlet-api:4.0.1")

  // Database dependencies
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  implementation("io.arrow-kt:arrow-core:2.2.1.1")
  implementation("io.hypersistence:hypersistence-utils-hibernate-71:3.15.2")

  // OpenAPI
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:2.1.4")
  implementation("io.awspring.cloud:spring-cloud-aws-starter:4.0.0")
  implementation("io.awspring.cloud:spring-cloud-aws-core:4.0.0")
  implementation("io.awspring.cloud:spring-cloud-aws-sns:4.0.0")
  implementation("io.awspring.cloud:spring-cloud-aws-sqs:4.0.0")
  implementation("org.springframework:spring-jms:7.0.4")
  implementation("org.apache.commons:commons-text:1.15.0")

  // SQS
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.0.0")

  // Test dependencies
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation("org.springframework.boot:spring-boot-webtestclient")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:5.1.0")
  testImplementation("io.swagger.parser.v3:swagger-parser-v2-converter:2.1.37")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
  testImplementation("org.testcontainers:testcontainers:2.0.3")
  testImplementation("org.testcontainers:localstack:1.21.4")
  testImplementation("org.testcontainers:postgresql:1.21.4")
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
  testImplementation("io.github.hakky54:logcaptor:2.12.2")
  testImplementation("org.mockito.kotlin:mockito-kotlin")
  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.0.0")
  testImplementation(kotlin("test"))
  if (project.hasProperty("docs")) {
    implementation("com.h2database:h2")
  }
}

jacoco {
  // You may modify the Jacoco version here
  toolVersion = "0.8.14"
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
  kotlinDaemonJvmArgs = listOf("-Xmx1g", "-Xms256m", "-XX:+UseParallelGC")
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
}

dependencyCheck {
  suppressionFiles.add("$rootDir/dependencyCheck/suppression.xml")
}

openApi {
  outputDir.set(layout.buildDirectory.dir("docs"))
  outputFileName.set("openapi.json")
  customBootRun.args.set(listOf("--spring.profiles.active=dev,localstack,docs"))
  customBootRun.environment.set(
    mapOf("AWS_REGION" to "eu-west-2"),
  )
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

data class ModelConfiguration(val name: String, val input: String, val output: String, val packageName: String)

val models = listOf(
  // https://adjustments-api-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildAdjustmentsApiModel",
    input = "adjustments-api-docs.json",
    output = "adjustmentsapi",
    packageName = "adjustmentsapi",
  ),
  // https://prison-api-dev.prison.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildPrisonApiModel",
    input = "prison-api-docs.json",
    output = "prisonapi",
    packageName = "prisonapi",
  ),
  // https://remand-and-sentencing-api-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildRemandAndSentencingApiModel",
    input = "remand-and-sentencing-api-docs.json",
    output = "remandandsentencing",
    packageName = "remandandsentencing",
  ),
  // https://nomis-sync-prisoner-mapping-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildNomisSyncMappingApiModel",
    input = "nomis-mapping-sync-api-docs.json",
    output = "nomissyncmapping",
    packageName = "nomissyncmapping",
  ),
  // https://manage-users-api-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildManageUsersApiModel",
    input = "manage-users-api-docs.json",
    output = "manageusersapi",
    packageName = "manageusersapi",
  ),
)

tasks {
  withType<KotlinCompile> {
    dependsOn(models.map { it.name })
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.name })
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.name })
  }
}

models.forEach {
  tasks.register(it.name, GenerateTask::class) {
    generatorName.set("kotlin")
    skipValidateSpec.set(true)
    inputSpec.set("openapi-specs/${it.input}")
    outputDir.set("$buildDirectory/generated/${it.output}")
    modelPackage.set("uk.gov.justice.digital.hmpps.calculatereleasedatesapi.${it.packageName}.model")
    apiPackage.set("uk.gov.justice.digital.hmpps.calculatereleasedatesapi.${it.packageName}.api")
    configOptions.set(configValues)
    globalProperties.set(mapOf("models" to ""))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
  }
}

val buildDirectory: Directory = layout.buildDirectory.get()
val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
)

kotlin {
  models.map { it.output }.forEach { generatedProject ->
    sourceSets["main"].apply {
      kotlin.srcDir("$buildDirectory/generated/$generatedProject/src/main/kotlin")
    }
  }
}

configure<KtlintExtension> {
  models.map { it.output }.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains("$buildDirectory/generated/$generatedProject/src/main/")
      }
    }
  }
}
