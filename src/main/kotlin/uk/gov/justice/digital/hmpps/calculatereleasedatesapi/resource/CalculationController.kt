package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DomainEventPublisher

@RestController
@RequestMapping("/calculation", produces = [MediaType.APPLICATION_JSON_VALUE])
class CalculationController(
  private val bookingService: BookingService,
  private val calculationService: CalculationService,
  private val domainEventPublisher: DomainEventPublisher,
) {
  @PostMapping(value = ["/{prisonerId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'CRD_ADMIN', 'PRISON')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates for a prisoner - preliminary calculation, this does not publish to NOMIS",
    description = "This endpoint will calculate release dates based on a prisoners latest booking - this is a " +
      "PRELIMINARY calculation that will not be published to NOMIS",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "CRD_ADMIN"),
      SecurityRequirement(name = "PRISON")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role")
    ]
  )
  fun calculate(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): BookingCalculation {
    log.info("Request received to calculate release dates for $prisonerId")
    return calculationService.calculate(bookingService.getBooking(prisonerId), PRELIMINARY)
  }

  @PostMapping(value = ["/{prisonerId}/confirm"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'CRD_ADMIN', 'PRISON')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates and persist the results for a prisoners latest booking",
    description = "This endpoint will calculate release dates based on a prisoners latest booking ",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "CRD_ADMIN"),
      SecurityRequirement(name = "PRISON")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role")
    ]
  )
  fun confirmCalculation(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): BookingCalculation {
    log.info("Request received to confirm release dates calculation for $prisonerId")
    val booking = bookingService.getBooking(prisonerId)
    val calculation = calculationService.calculate(booking, CONFIRMED)
    domainEventPublisher.publishReleaseDateChange(prisonerId, booking.bookingId)
    return calculation
  }

  @GetMapping(value = ["/results/{prisonerId}/{bookingId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'CRD_ADMIN', 'PRISON')")
  @ResponseBody
  @Operation(
    summary = "Get confirmed release dates for a prisoner's specific booking",
    description = "This endpoint will return the confirmed release dates based on a prisoners booking",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "CRD_ADMIN"),
      SecurityRequirement(name = "PRISON")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No confirmed calculation exists for this prisoner and booking")
    ]
  )
  fun getConfirmedCalculationResults(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @Parameter(required = true, example = "100001", description = "The booking ID associated with the calculation")
    @PathVariable("bookingId")
    bookingId: Long,
  ): BookingCalculation {
    log.info("Request received return calculation results for prisoner {} and bookingId ", prisonerId, bookingId)
    return calculationService.findConfirmedCalculationResults(prisonerId, bookingId)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
