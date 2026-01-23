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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService

@RestController
@RequestMapping("/calculationReference", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "calculation-reference-controller", description = "Operations involving a calculation, using a calculation reference")
class CalculationReferenceController(
  private val calculationTransactionalService: CalculationTransactionalService,
) {

  @GetMapping(value = ["/{calculationReference}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__CALCULATE__RW', 'CALCULATE_RELEASE_DATES__CALCULATE__RO')")
  @ResponseBody
  @Operation(
    summary = "Get release dates for a calculationRequestId",
    description = "This endpoint will return the release dates based on a calculationRequestId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun getCalculationResults(
    @Parameter(required = true, example = "123ABC", description = "The calculationRequestId of the results")
    @PathVariable("calculationReference")
    calculationReference: String,
    @RequestParam("checkForChange", required = false, defaultValue = "false") checkForChange: Boolean,
  ): CalculatedReleaseDates {
    log.info("Request received return calculation results for calculationRequestId {}", calculationReference)
    return calculationTransactionalService.findCalculationResultsByCalculationReference(calculationReference, checkForChange)
  }
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
