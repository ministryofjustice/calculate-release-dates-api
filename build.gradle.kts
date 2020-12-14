plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "2.1.0"
  kotlin("plugin.spring") version "1.4.21"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
}
