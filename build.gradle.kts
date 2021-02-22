plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.1.1"
  kotlin("plugin.spring") version "1.4.30"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
}
