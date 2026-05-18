package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Consumer

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version ?: ""

  @Bean
  fun customOpenAPI(): OpenAPI? = OpenAPI()
    .info(
      Info()
        .title("Calculate Release Dates API")
        .version(version)
        .description("API for calculating a release date")
        .contact(
          Contact()
            .name("HMPPS Digital Studio")
            .email("feedback@digital.justice.gov.uk"),
        ),
    )

  /*
   * There is currently a bug with OpenAPI/Swagger/Spring Boot where using @JsonUnwrapped can cause the schema generation to fail.
   * https://github.com/swagger-api/swagger-core/issues/5126
   * This is the suggested workaround from the bug and can be removed once the issue is resolve.
   */
  @Bean
  fun nullKeyCleanupCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi: OpenAPI ->
    if (openApi.components != null && openApi.components.schemas != null) {
      openApi.components.schemas.values.forEach(Consumer { schema: Schema<*>? -> this.removeNullKeys(schema) })
    }
  }

  private fun removeNullKeys(schema: Schema<*>?) {
    if (schema?.properties != null) {
      schema.properties.entries.removeIf { e -> e.key == null }
      schema.properties.values.forEach { schema: Schema<*> -> this.removeNullKeys(schema) }
    }
  }
}
