package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ProgressionModelExclusionResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.ManualCalculationController.Companion.log
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AdjustmentsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceAndOffenceService

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "source-data-proxy-controller", description = "Proxies source data and adds info about changes since last calc")
class SourceDataProxyController(
  val sentenceAndOffenceService: SentenceAndOffenceService,
  val adjustmentsService: AdjustmentsService,
  val featureToggles: FeatureToggles,
) {
  @GetMapping(value = ["/sentence-and-offence-information/{bookingId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__CALCULATE__RW', 'CALCULATE_RELEASE_DATES__CALCULATE__RO')")
  @ResponseBody
  @Operation(
    summary = "Get sentence and offence information",
    description = "This endpoint will return a response model which lists sentence and offence information. It will notify if there have been any changed since last calculation",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a List<AnalysedSentenceAndOffences"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getSentencesAndOffences(@PathVariable bookingId: Long): List<AnalysedSentenceAndOffence> = sentenceAndOffenceService.getSentencesAndOffences(bookingId)

  @GetMapping(value = ["/booking-and-sentence-adjustments/{bookingId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__CALCULATE__RW', 'CALCULATE_RELEASE_DATES__CALCULATE__RO')")
  @ResponseBody
  @Operation(
    summary = "Get booking and sentence adjustments",
    description = "This endpoint will return a response model which shows booking and sentence adjustments. It will notify if there are new adjustments since last calculation",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a List<AnalysedBookingAndSentenceAdjustments"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getBookingAndSentenceAdjustments(@PathVariable bookingId: Long): AnalysedBookingAndSentenceAdjustments = adjustmentsService.getAnalysedBookingAndSentenceAdjustments(bookingId)

  @GetMapping(value = ["/adjustments/{prisonerId}"])
  @PreAuthorize("hasAnyRole('VIEW_PRISONER_DATA', 'RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__CALCULATE__RW', 'CALCULATE_RELEASE_DATES__CALCULATE__RO')")
  @ResponseBody
  @Operation(
    summary = "Get adjustments",
    description = "This endpoint will return a response model which shows adjustments. It will notify if there are new adjustments since last calculation",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a List<AnalysedAdjustment"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getAdjustments(@PathVariable prisonerId: String): List<AnalysedAdjustment> = adjustmentsService.getAnalysedAdjustments(prisonerId)

  @GetMapping(value = ["/has-offences-excluded-from-progression-model/{prisonerId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__CALCULATE__RW', 'CALCULATE_RELEASE_DATES__CALCULATE__RO')")
  @ResponseBody
  @Operation(
    summary = "Determine if a prisoners latest booking has any offences appearing on SA2026 Excluded Offences for Progression Model",
    description = "This endpoint will return true if the prisoners latest booking has any offences appearing on SA2026 Excluded Offences for Progression Model.\n Does not check Schedule 13 Part 3 which is a separate exclusion for Progression Model.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a boolean value"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun hasOffencesExcludedFromProgressionModel(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable prisonerId: String,
  ): ProgressionModelExclusionResponse {
    log.info("Request received to check if prisoners latest booking has PM exclusions: $prisonerId")
    if (featureToggles.progressionModelScheduleExclusionEnabled) {
      return ProgressionModelExclusionResponse(containsOffenceExcludedFromProgressionModel = sentenceAndOffenceService.hasOffencesExcludedFromProgressionModelNotIncludingSchedule13Part3(prisonerId))
    }
    throw CrdWebException("Progression Model exclusion not supported in this environment yet", HttpStatus.BAD_REQUEST)
  }
}
