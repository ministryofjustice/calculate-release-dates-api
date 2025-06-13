package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.InactiveDataOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

@RestController
@RequestMapping("/validation", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "validation-controller", description = "Validation related requests")
class ValidationController(
  private val calculationTransactionalService: CalculationTransactionalService,
) {
  @PostMapping(value = ["/{prisonerId}/full-validation"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Validates that the data for the given prisoner in NOMIS can be used to calculate a release date",
    description = "This endpoint will validate that the data for the given prisoner in NOMIS can be supported by the " +
      "calculate release dates engine",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Validation job has run successfully, the response indicates if there are any errors"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun validate(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @Parameter(required = false, description = "Include inactive data within validation. Defaults to false")
    @RequestParam("includeInactiveData")
    includeInactiveData: Boolean?,
    @RequestBody
    calculationUserInputs: CalculationUserInputs?,
  ): List<ValidationMessage> {
    log.info("Request received to validate prisonerId $prisonerId")
    return calculationTransactionalService.fullValidation(
      prisonerId,
      calculationUserInputs ?: CalculationUserInputs(),
      if (includeInactiveData == true) InactiveDataOptions.overrideToIncludeInactiveData() else InactiveDataOptions.default(),
    )
  }

  @GetMapping(value = ["/{prisonerId}/supported-validation"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Validates that the sentences for the given prisoner in NOMIS can be used to calculate a release date",
    description = "This endpoint will validate that the data for the given prisoner in NOMIS can be supported by the " +
      "calculate release dates engine",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Validation job has run successfully, the response indicates if there are any errors"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun validateSupported(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): List<ValidationMessage> {
    log.info("Request received to validate prisonerId $prisonerId")
    return calculationTransactionalService.supportedValidation(prisonerId)
  }

  @GetMapping(value = ["/{prisonerId}/manual-entry-validation"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Validates that the sentences for the given prisoner in NOMIS are ok adequate to record a manual date against for unsupported types",
    description = "This endpoint will validate that the data for the given prisoner in NOMIS is of sufficient quality " +
      "to allow a manual date to be recorded via CRD",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Validation job has run successfully, the response indicates if there are any errors"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun validateForManualEntry(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomisId)")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): List<ValidationMessage> {
    log.info("Request received to validate prisonerId for manual date entry $prisonerId")
    val errorMessages = calculationTransactionalService.validateForManualBooking(prisonerId)
    return errorMessages
  }

  @GetMapping(value = ["/manual-entry-dates-validation"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Validates that requested calculation dates are valid",
    description = "Some dates cannot be calculated together, while some dates may require another date to be valid",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Validation job has run successfully, the response indicates if there are any errors"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun validateForDatesManualEntry(
    @RequestParam releaseDates: List<String>,
  ): List<ValidationMessage> {
    log.info("Request received to validate calculation dates for ${releaseDates.size} dates")
    return calculationTransactionalService.validateRequestedDates(releaseDates)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
