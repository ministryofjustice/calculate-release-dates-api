package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import arrow.core.getOrElse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.persistence.EntityNotFoundException
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.TEST
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoActiveBookingException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDatesAndCalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationBreakdownService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DetailedCalculationResultsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.InactiveDataOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.LatestCalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.OffenderKeyDatesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RecordARecallService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RelevantRemandService

@RestController
@RequestMapping("/calculation", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "calculation-controller", description = "Operations involving a calculation")
class CalculationController(
  private val calculationTransactionalService: CalculationTransactionalService,
  private val relevantRemandService: RelevantRemandService,
  private val detailedCalculationResultsService: DetailedCalculationResultsService,
  private val latestCalculationService: LatestCalculationService,
  private val calculationBreakdownService: CalculationBreakdownService,
  private val offenderKeyDatesService: OffenderKeyDatesService,
  private val recordARecallService: RecordARecallService,
) {
  @PostMapping(value = ["/{prisonerId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates for a prisoner - preliminary calculation, this does not publish to NOMIS",
    description = "This endpoint will calculate release dates based on a prisoners latest booking - this is a " +
      "PRELIMINARY calculation that will not be published to NOMIS",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "500", description = "Unable to perform calculation"),
    ],
  )
  fun calculate(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @RequestBody
    calculationRequestModel: CalculationRequestModel,
  ): CalculatedReleaseDates {
    log.info("Request received to calculate release dates for $prisonerId")
    return calculationTransactionalService.calculate(prisonerId, calculationRequestModel)
  }

  @PostMapping(value = ["/{prisonerId}/test"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates for a prisoner - test calculation, this does not publish to NOMIS",
    description = "This endpoint will calculate release dates based on a prisoners latest booking, this can include" +
      "inactive bookings of historic prisoners. Endpoint is used to test calculations against NOMIS.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun testCalculation(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @RequestBody
    calculationRequestModel: CalculationRequestModel,
  ): CalculationResults {
    log.info("Request received to calculate release dates for $prisonerId")
    val validationMessages = calculationTransactionalService.fullValidation(
      prisonerId,
      calculationRequestModel.calculationUserInputs ?: CalculationUserInputs(),
      InactiveDataOptions.overrideToIncludeInactiveData(),
    )

    return if (validationMessages.isNotEmpty()) {
      CalculationResults(validationMessages = validationMessages)
    } else {
      CalculationResults(
        calculationTransactionalService.calculate(
          prisonerId,
          calculationRequestModel,
          InactiveDataOptions.overrideToIncludeInactiveData(),
          TEST,
        ),
      )
    }
  }

  @PostMapping(value = ["/confirm/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates and persist the results for a prisoners latest booking",
    description = "This endpoint will calculate release dates based on a prisoners latest booking ",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(
        responseCode = "404",
        description = "No calculation exists for the passed calculationRequestId or the write to NOMIS has failed",
      ),
      ApiResponse(
        responseCode = "412",
        description = "The booking data that was used for the preliminary calculation has changed",
      ),
    ],
  )
  fun confirmCalculation(
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
    @RequestBody
    submitCalculationRequest: SubmitCalculationRequest,
  ): CalculatedReleaseDates {
    log.info("Request received to confirm release dates calculation for $calculationRequestId")
    return calculationTransactionalService.validateAndConfirmCalculation(calculationRequestId, submitCalculationRequest)
  }

  @GetMapping(value = ["/results/{prisonerId}/{bookingId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get confirmed release dates for a prisoner's specific booking",
    description = "This endpoint will return the confirmed release dates based on a prisoners booking",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No confirmed calculation exists for this prisoner and booking"),
    ],
  )
  fun getConfirmedCalculationResults(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @Parameter(required = true, example = "100001", description = "The booking ID associated with the calculation")
    @PathVariable("bookingId")
    bookingId: Long,
  ): CalculatedReleaseDates {
    log.info("Request received return calculation results for prisoner {} and bookingId {}", prisonerId, bookingId)
    return calculationTransactionalService.findConfirmedCalculationResults(prisonerId, bookingId)
  }

  @GetMapping(value = ["/results/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
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
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the results")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): CalculatedReleaseDates {
    log.info("Request received return calculation results for calculationRequestId {}", calculationRequestId)
    return calculationTransactionalService.findCalculationResults(calculationRequestId)
  }

  @GetMapping(value = ["/detailed-results/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get release dates for a calculationRequestId with additional details",
    description = "This endpoint will return the release dates based on a calculationRequestId along with hints and full descriptions.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun getDetailedResults(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the results")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): DetailedCalculationResults {
    log.info("Request received return detailed results for calculationRequestId {}", calculationRequestId)
    return detailedCalculationResultsService.findDetailedCalculationResults(calculationRequestId)
  }

  @GetMapping(value = ["/breakdown/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get breakdown for a calculationRequestId",
    description = "This endpoint will return the breakdown based on a calculationRequestId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns breakdown of calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun getCalculationBreakdown(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the breakdown")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): CalculationBreakdown {
    log.info("Request received return calculation breakdown for calculationRequestId {}", calculationRequestId)
    return calculationBreakdownService.getBreakdownUnsafely(calculationRequestId)
  }

  @GetMapping(value = ["/sentence-and-offences/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get sentences and offences for a calculationRequestId",
    description = "This endpoint will return the sentences and offences based on a calculationRequestId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns sentences and offences"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun getSentencesAndOffence(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): List<SentenceAndOffenceWithReleaseArrangements> {
    log.info("Request received to get sentences and offences from $calculationRequestId calculation")
    return calculationTransactionalService.findSentenceAndOffencesFromCalculation(calculationRequestId)
  }

  @GetMapping(value = ["/prisoner-details/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get prisoner details for a calculationRequestId",
    description = "This endpoint will return the prisoner details based on a calculationRequestId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns prisoner details"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun getPrisonerDetails(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): PrisonerDetails {
    log.info("Request received to get prisoner details from $calculationRequestId calculation")
    return calculationTransactionalService.findPrisonerDetailsFromCalculation(calculationRequestId)
  }

  @GetMapping(value = ["/return-to-custody/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get return to custody date for a calculationRequestId",
    description = "This endpoint will return the return to custody date based on a calculationRequestId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns return to custody"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun getReturnToCustodyDate(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): ReturnToCustodyDate {
    log.info("Request received to get return to custody date from $calculationRequestId calculation")
    return calculationTransactionalService.findReturnToCustodyDateFromCalculation(calculationRequestId)
      ?: throw EntityNotFoundException("No return to custody date exists for calculationRequestId $calculationRequestId ")
  }

  @GetMapping(value = ["/calculation-user-input/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get user input for a calculationRequestId",
    description = "This endpoint will return the user input based on a calculationRequestId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculation inputs"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun getCalculationInput(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): CalculationUserInputs {
    log.info("Request received to get user input from $calculationRequestId calculation")
    return calculationTransactionalService.findUserInput(calculationRequestId)
  }

  @GetMapping(value = ["/adjustments/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get adjustments for a calculationRequestId",
    description = "This endpoint will return the adjustments based on a calculationRequestId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns adjustments"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun get(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): BookingAndSentenceAdjustments {
    log.info("Request received to get booking and sentence adjustments from $calculationRequestId calculation")
    return calculationTransactionalService.findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId)
  }

  // TODO remove params and GET mapping above once adjustments API integration is live.
  @GetMapping(value = ["/adjustments/{calculationRequestId}"], params = ["adjustments-api"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get adjustments for a calculationRequestId",
    description = "This endpoint will return the adjustments based on a calculationRequestId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns adjustments"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId"),
    ],
  )
  fun getAdjustments(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): List<AdjustmentDto> {
    log.info("Request received to get adjustments from $calculationRequestId calculation")
    return calculationTransactionalService.findAdjustmentsFromCalculation(calculationRequestId)
  }

  @PostMapping(value = ["/relevant-remand/{prisonerId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Calculate a release date at a point in time for the relevant remand tool.",
    description = "This endpoint calculates the release date of an intersecting sentence, this is needed by the" +
      "relevant remand tool in order to work out remand periods.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun relevantRemandCalculation(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @RequestBody
    relevantRemandCalculationRequest: RelevantRemandCalculationRequest,
  ): RelevantRemandCalculationResult = relevantRemandService.relevantRemandCalculation(prisonerId, relevantRemandCalculationRequest)

  @GetMapping(value = ["/{prisonerId}/latest"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get latest release dates for a prisoner",
    description = "This endpoint will return the latest release dates for a prisoner. They may come from CRDS or NOMIS as identified by the source in the results.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "The prisoner could not be found or there are no calculations yet"),
    ],
  )
  fun getLatestCalculation(
    @Parameter(required = true, example = "ABC123", description = "The id of the prisoner")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): LatestCalculation {
    log.info("Request received return latest calculation for prisoner {}", prisonerId)
    val result = latestCalculationService.latestCalculationForPrisoner(prisonerId)
    return result.getOrElse { problemMessage -> throw NoActiveBookingException(problemMessage) }
  }

  @GetMapping(value = ["/nomis-calculation-summary/{offenderSentCalculationId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get Nomis calculation summary with release dates for a offenderSentCalculationId",
    description = "This endpoint will return the nomis calculation summary with release dates based on a offenderSentCalculationId",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns Nomis calculation summary with release dates based on a offenderSentCalculationId"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No nomis calculation summary - release dates exists for this offenderSentCalculationId"),
    ],
  )
  fun getNomisCalculationSummary(
    @Parameter(required = true, example = "123456", description = "The offenderSentCalculationId of the offender booking or a calculation")
    @PathVariable("offenderSentCalculationId")
    offenderSentCalculationId: Long,
  ): NomisCalculationSummary {
    log.info("Request received to get offender key dates for $offenderSentCalculationId")
    return offenderKeyDatesService.getNomisCalculationSummary(offenderSentCalculationId)
  }

  @GetMapping(value = ["/nomis-calculation-summary/booking/{bookingId}/calculation/{offenderSentCalculationId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get Nomis calculation summary with release dates (including overrides) for a offenderSentCalculationId",
    description = "This endpoint will return the nomis calculation summary with release dates based on a offenderSentCalculationId. Any date overridden in Nomis will be highlighted",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns Nomis calculation summary with release dates based on a offenderSentCalculationId"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No nomis calculation summary - release dates exists for this offenderSentCalculationId"),
    ],
  )
  fun getNomisCalculationSummaryWithOverrides(
    @Parameter(required = true, example = "123456", description = "The offenderSentCalculationId of the offender booking or a calculation")
    @PathVariable("offenderSentCalculationId")
    offenderSentCalculationId: Long,
    @PathVariable("bookingId")
    bookingId: Long,
  ): NomisCalculationSummary {
    log.info("Request received to get offender key dates with overrides for $offenderSentCalculationId")
    return offenderKeyDatesService.getNomisCalculationSummary(offenderSentCalculationId, bookingId)
  }

  @GetMapping(value = ["/release-dates/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get release dates summary for a calculation request id",
    description = "This endpoint will return the list of release dates based on a calculation request id",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns list of release dates based on a calculation request id"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No release dates exists for this calculation request id"),
    ],
  )
  fun getKeyDatesForABooking(
    @Parameter(required = true, example = "123456", description = "The calculation request id of the offender")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): ReleaseDatesAndCalculationContext {
    log.info("Request received to get offender key dates for calc Id - $calculationRequestId")
    return offenderKeyDatesService.getKeyDatesByCalcId(calculationRequestId)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
