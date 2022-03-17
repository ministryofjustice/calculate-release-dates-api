package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import java.time.LocalDate
import javax.validation.constraints.NotEmpty

@RestController
@RequestMapping("/test", produces = [MediaType.APPLICATION_JSON_VALUE])
class TestController(
  private val calculationTransactionalService: CalculationTransactionalService,
) {

  //  TODO this is a temporary endpoint to aid diagnosis of calculation errors whilst in private beta - this whole
  //   controller will eventually be removed
  @PostMapping(value = ["/calculation-by-booking"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates based on a prisoners booking data",
    description = "This endpoint will calculate release dates based on a prisoners booking data " +
      "(e.g. sentences and adjustments)",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role")
    ]
  )
  fun calculate(@RequestBody @NotEmpty booking: Booking): BookingCalculation {
    log.info("Request received to calculate booking for $booking")
    val fakeSourceData = PrisonApiSourceData(
      emptyList(), PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3)),
      BookingAndSentenceAdjustments(
        emptyList(), emptyList()
      ),
      null
    )
    return calculationTransactionalService.calculate(booking, PRELIMINARY, fakeSourceData)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
