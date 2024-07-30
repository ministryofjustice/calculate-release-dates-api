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
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManualCalculationService

@RestController
@RequestMapping("/manual-calculation", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "manual-calculation-controller", description = "Operations involving a manual calculation")
class ManualCalculationController(
  private val manualCalculationService: ManualCalculationService,
) {
  @GetMapping(value = ["/{bookingId}/has-indeterminate-sentences"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Determine if a booking has any indeterminate sentences",
    description = "This endpoint will return true if a booking has any indeterminate sentences",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a boolean value"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun hasIndeterminateSentences(
    @Parameter(required = true, example = "100001", description = "The booking ID to check against")
    @PathVariable("bookingId")
    bookingId: Long,
  ): Boolean {
    log.info("Request received to check if booking has indeterminate sentences for bookingId: $bookingId")
    return manualCalculationService.hasIndeterminateSentences(bookingId)
  }

  @GetMapping(value = ["/{bookingId}/has-recall-sentences"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Determine if a booking has any recall sentences",
    description = "This endpoint will return true if a booking has any recall sentences",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a boolean value"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun hasRecallSentences(
    @Parameter(required = true, example = "100001", description = "The booking ID to check against")
    @PathVariable("bookingId")
    bookingId: Long,
  ): Boolean {
    log.info("Request received to check if booking has recall sentences for bookingId: $bookingId")
    return manualCalculationService.hasRecallSentences(bookingId)
  }

  @PostMapping(value = ["/{prisonerId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Store a manual calculation",
    description = "This endpoint will return a response model which indicates the success of storing a manual calculation",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "201", description = "Returns a ManualCalculationResponse"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun storeManualCalculation(
    @PathVariable prisonerId: String,
    @RequestBody manualEntryRequest: ManualEntryRequest,
  ): ManualCalculationResponse {
    return manualCalculationService.storeManualCalculation(prisonerId, manualEntryRequest)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
