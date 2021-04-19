plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.1.7"
  kotlin("plugin.spring") version "1.4.32"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
}

tasks {
  compileKotlin {
    kotlinOptions {
      jvmTarget = "15"
    }
  }
}
