package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.LegacySentenceCalculationService

@RestController
@RequestMapping("/legacy-data", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "legacy-data-controller", description = "Provides migrate and sync APIs for managing legacy data")
@Validated
class LegacyDataController(private val legacySentenceCalculationService: LegacySentenceCalculationService) {

  @PostMapping(value = ["/migrate/{prisonerId}"])
  @ResponseBody
  @Operation(
    summary = "Migrate sentence calculations from NOMIS into CRDS",
    description = "Creates an entry in the legacy sentence calculation table for use in calculation history.\n\n " +
      "If possible we link it to an existing CRDS calculation otherwise it is left as a NOMIS calculation for historical purposes.\n\n" +
      "Migrating a prisoner will first remove all current entries in the legacy sentence calculation table for that prisoner meaning it can be retried from scratch if required.\n\n",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "If we successfully migrated the sentence calculations to CRDS"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun migrate(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @RequestBody
    request: LegacySentenceCalculationMigrationRequest,
  ): LegacySentenceCalculationMigrationResponse {
    LOGGER.info("Migrating legacy sentence calculations for prisoner $prisonerId")
    return legacySentenceCalculationService.migrate(prisonerId, request)
  }

  @PostMapping(value = ["/sync"])
  @ResponseBody
  @Operation(
    summary = "Sync a single sentence calculation from NOMIS into CRDS",
    description = "Creates an entry in the legacy sentence calculation table for use in calculation history.\n\n " +
      "If possible we link it to an existing CRDS calculation otherwise it is left as a NOMIS calculation for historical purposes.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "If we successfully synced the sentence calculation to CRDS"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun sync(@RequestBody request: LegacySentenceCalculationDto): LegacySentenceCalculationToCrdsMappingResult {
    LOGGER.info("Syncing legacy sentence calculation for prisoner $${request.prisonerId} with id ${request.nomisCalculationId}")
    return legacySentenceCalculationService.sync(request)
  }

  companion object {
    private val LOGGER = LoggerFactory.getLogger(LegacyDataController::class.java)
  }
}
