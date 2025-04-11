package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AdjustmentsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentenceAndOffenceService

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "source-data-proxy-controller", description = "Proxies source data and adds info about changes since last calc")
class SourceDataProxyController(
  val sentenceAndOffenceService: SentenceAndOffenceService,
  val adjustmentsService: AdjustmentsService,
) {
  @GetMapping(value = ["/sentence-and-offence-information/{bookingId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get sentence and offence information",
    description = "This endpoint will return a response model which lists sentence and offence information. It will notify if there have been any changed since last calculation",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a List<AnalyzedSentenceAndOffences"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getSentencesAndOffences(@PathVariable bookingId: Long): List<AnalysedSentenceAndOffence> {
    return sentenceAndOffenceService.getSentencesAndOffences(bookingId)
  }

  @GetMapping(value = ["/booking-and-sentence-adjustments/{bookingId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get booking and sentence adjusments",
    description = "This endpoint will return a response model which shows booking and sentence adjustments. It will notify if there are new adjustments since last calculation",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a List<AnalyzedBookingAndSentenceAdjustments"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getBookingAndSentenceAdjustments(@PathVariable bookingId: Long): AnalyzedBookingAndSentenceAdjustments {
    return adjustmentsService.getAnalyzedBookingAndSentenceAdjustments(bookingId)
  }

  @GetMapping(value = ["/adjustments/{prisonerId}"])
  @PreAuthorize("hasAnyRole('VIEW_PRISONER_DATA', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get adjustments",
    description = "This endpoint will return a response model which shows adjustments. It will notify if there are new adjustments since last calculation",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a List<AnalyzedAdjustment"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getAdjustments(@PathVariable prisonerId: String): List<AnalyzedAdjustment> {
    return adjustmentsService.getAnalyzedAdjustments(prisonerId)
  }
}
