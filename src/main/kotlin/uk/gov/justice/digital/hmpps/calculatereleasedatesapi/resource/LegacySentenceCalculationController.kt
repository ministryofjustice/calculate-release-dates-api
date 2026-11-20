package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
@RequestMapping("/legacy-sentence-calculation", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "legacy-sentence-calculation-controller", description = "Provides migrate and sync APIs for managing legacy sentence calculations")
@Validated
class LegacySentenceCalculationController(private val legacySentenceCalculationService: LegacySentenceCalculationService) {

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
    summary = "Sync a new sentence calculation from NOMIS into CRDS",
    description = "Creates an entry in the legacy sentence calculation table for use in calculation history.\n\n " +
      "If possible we link it to an existing CRDS calculation otherwise it is left as a NOMIS calculation for historical purposes.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "If we successfully synced the sentence calculation to CRDS"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun syncNew(@RequestBody request: LegacySentenceCalculationDto): LegacySentenceCalculationToCrdsMappingResult {
    LOGGER.info("Syncing new legacy sentence calculation for prisoner $${request.prisonerId} with id ${request.nomisCalculationId}")
    return legacySentenceCalculationService.syncNew(request)
  }

  @PutMapping(value = ["/sync"])
  @ResponseBody
  @Operation(
    summary = "Sync an update to an sentence calculation from NOMIS into CRDS",
    description = "Updates an existing sentence calculation that has already been imported via sync new or migration.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "If we successfully synced the sentence calculation to CRDS"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun syncUpdated(@RequestBody request: LegacySentenceCalculationDto): LegacySentenceCalculationToCrdsMappingResult {
    LOGGER.info("Syncing updated legacy sentence calculation for prisoner $${request.prisonerId} with id ${request.nomisCalculationId}")
    return legacySentenceCalculationService.syncUpdated(request)
  }

  @DeleteMapping(value = ["/sync/{nomisCalculationId}"])
  @ResponseBody
  @Operation(
    summary = "Sync a deleted sentence calculation from NOMIS into CRDS",
    description = "Deletes an existing sentence calculation that has already been imported via sync new or migration.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "204", description = "If we successfully deleted the sentence calculation from CRDS"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun syncDeleted(
    @Parameter(required = true, example = "123456", description = "The the id of the sentence calc from NOMIS")
    @PathVariable("nomisCalculationId")
    nomisCalculationId: Long,
  ): ResponseEntity<Unit> {
    LOGGER.info("Syncing deleted legacy sentence calculation with id $nomisCalculationId")
    legacySentenceCalculationService.syncDeleted(nomisCalculationId)
    return ResponseEntity.noContent().build()
  }

  @GetMapping(value = ["/{prisonerId}"])
  @ResponseBody
  @Operation(
    summary = "Get all legacy sentence calculations for a prisoner - FOR RECONCILIATION ONLY",
    description = "Gets all legacy sentence calculations for a prisoner\n\n " +
      "This is to be used for migrate and sync reconciliation purposes only and not for any user facing feature. Use the regular consumer APIs if you need historical calculation data.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "All legacy sentence calculations that have been migrated or synced to CRDS"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun getAllForPrisoner(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): List<LegacySentenceCalculationDto> {
    LOGGER.info("Fetching legacy sentence calculations for prisoner $prisonerId")
    return legacySentenceCalculationService.getAllForPrisoner(prisonerId)
  }

  companion object {
    private val LOGGER = LoggerFactory.getLogger(LegacySentenceCalculationController::class.java)
  }
}
