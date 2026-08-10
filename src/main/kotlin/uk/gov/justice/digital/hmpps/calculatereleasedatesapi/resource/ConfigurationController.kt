package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ConfigItem
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/configuration", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "configuration-controller", description = "Provides basic reference data")
@Validated
class ConfigurationController(
  private val featureToggles: FeatureToggles,
  private val sdsLegislationConfiguration: SDSLegislationConfiguration,
) {

  @GetMapping(value = ["/all"])
  @ResponseBody
  @Operation(
    summary = "Get the current configuration of this environment",
    description = "Returns the feature toggles and other important information about the current environment",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Configuration and feature toggles"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun getAllConfiguration(): List<ConfigItem> {
    val pmCommencementDate = sdsLegislationConfiguration.progressionModelLegislation?.commencementDate()
    val progressionModelConfig = if (pmCommencementDate != null) {
      ConfigItem("Progression Model commencement", DateTimeFormatter.ISO_DATE.format(pmCommencementDate))
    } else {
      ConfigItem("Progression Model commencement", "Disabled")
    }
    return featureToggles.toConfigItems() + listOf(progressionModelConfig)
  }
}
