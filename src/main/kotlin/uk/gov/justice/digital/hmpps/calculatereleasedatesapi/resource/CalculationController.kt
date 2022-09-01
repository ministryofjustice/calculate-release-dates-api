package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.TEST
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdCalculationValidationException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserQuestions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceDiagram
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationUserQuestionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessages
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType
import javax.persistence.EntityNotFoundException

@RestController
@RequestMapping("/calculation", produces = [MediaType.APPLICATION_JSON_VALUE])
class CalculationController(
  private val bookingService: BookingService,
  private val prisonService: PrisonService,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val calculationService: CalculationService,
  private val validationService: ValidationService,
  private val calculationUserQuestionService: CalculationUserQuestionService
) {
  @PostMapping(value = ["/{prisonerId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates for a prisoner - preliminary calculation, this does not publish to NOMIS",
    description = "This endpoint will calculate release dates based on a prisoners latest booking - this is a " +
      "PRELIMINARY calculation that will not be published to NOMIS",
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
  fun calculate(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @RequestBody
    calculationUserInputs: CalculationUserInputs?
  ): CalculatedReleaseDates {
    log.info("Request received to calculate release dates for $prisonerId")
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId)
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    try {
      return calculationTransactionalService.calculate(booking, PRELIMINARY, sourceData, calculationUserInputs)
    } catch (error: Exception) {
      calculationTransactionalService.recordError(booking, sourceData, calculationUserInputs, error)
      throw error
    }
  }

  @PostMapping(value = ["/{prisonerId}/test"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates for a prisoner - test calculation, this does not publish to NOMIS",
    description = "This endpoint will calculate release dates based on a prisoners latest booking, this can include" +
            "inactive bookings of historic prisoners. Endpoint is used to test calculations against NOMIS.",
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
  fun testCalculation(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @RequestBody
    calculationUserInputs: CalculationUserInputs?
  ): CalculatedReleaseDates {
    log.info("Request received to calculate release dates for $prisonerId")
    val sourceData = prisonService.getPrisonApiSourceDataIncludingInactive(prisonerId)
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    try {
      return calculationTransactionalService.calculate(booking, TEST, sourceData, calculationUserInputs)
    } catch (error: Exception) {
      calculationTransactionalService.recordError(booking, sourceData, calculationUserInputs, error)
      throw error
    }
  }

  @PostMapping(value = ["/{prisonerId}/confirm/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates and persist the results for a prisoners latest booking",
    description = "This endpoint will calculate release dates based on a prisoners latest booking ",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(
        responseCode = "404",
        description = "No calculation exists for the passed calculationRequestId or the write to NOMIS has failed"
      ),
      ApiResponse(
        responseCode = "412",
        description = "The booking data that was used for the preliminary calculation has changed"
      ),
    ]
  )
  fun confirmCalculation(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @Parameter(
      required = true,
      example = "1234567",
      description = "The calculation request ID of the calculation to be confirmed"
    )
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
    @RequestBody
    calculationFragments: CalculationFragments
  ): CalculatedReleaseDates {
    log.info("Request received to confirm release dates calculation for $prisonerId")
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId)
    val userInput = calculationTransactionalService.findUserInput(calculationRequestId)
    val booking = bookingService.getBooking(sourceData, userInput)
    calculationTransactionalService.validateConfirmationRequest(calculationRequestId, booking)
    try {
      val calculation = calculationTransactionalService.calculate(booking, CONFIRMED, sourceData, userInput, calculationFragments)
      calculationTransactionalService.writeToNomisAndPublishEvent(prisonerId, booking, calculation)
      return calculation
    } catch (error: Exception) {
      calculationTransactionalService.recordError(booking, sourceData, userInput, error)
      throw error
    }
  }

  @GetMapping(value = ["/results/{prisonerId}/{bookingId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get confirmed release dates for a prisoner's specific booking",
    description = "This endpoint will return the confirmed release dates based on a prisoners booking",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
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
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId")
    ]
  )
  fun getCalculationResults(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the results")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): CalculatedReleaseDates {
    log.info("Request received return calculation results for calculationRequestId {}", calculationRequestId)
    return calculationTransactionalService.findCalculationResults(calculationRequestId)
  }

  @GetMapping(value = ["/breakdown/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get breakdown for a calculationRequestId",
    description = "This endpoint will return the breakdown based on a calculationRequestId",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId")
    ]
  )
  fun getCalculationBreakdown(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the breakdown")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): CalculationBreakdown {
    log.info("Request received return calculation breakdown for calculationRequestId {}", calculationRequestId)
    val sentencesAndOffences = calculationTransactionalService.findSentenceAndOffencesFromCalculation(calculationRequestId)
    val prisonerDetails = calculationTransactionalService.findPrisonerDetailsFromCalculation(calculationRequestId)
    val adjustments = calculationTransactionalService.findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId)
    val returnToCustodyDate = calculationTransactionalService.findReturnToCustodyDateFromCalculation(calculationRequestId)
    val calculation = calculationTransactionalService.findCalculationResults(calculationRequestId)
    val userInput = calculationTransactionalService.findUserInput(calculationRequestId)
    val sourceData = PrisonApiSourceData(sentencesAndOffences, prisonerDetails, adjustments, returnToCustodyDate)

    return calculationTransactionalService.calculateWithBreakdown(bookingService.getBooking(sourceData, userInput), calculation)
  }

  @GetMapping(value = ["/diagram/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get data to build a sentence diagram",
    description = "This endpoint will return the data required for a sentence diagram for the given calculationRequestId",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId")
    ]
  )
  fun getSentenceDiagram(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the diagram")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): SentenceDiagram {
    log.info("Request received return calculation breakdown for calculationRequestId {}", calculationRequestId)
    val sentencesAndOffences = calculationTransactionalService.findSentenceAndOffencesFromCalculation(calculationRequestId)
    val prisonerDetails = calculationTransactionalService.findPrisonerDetailsFromCalculation(calculationRequestId)
    val adjustments = calculationTransactionalService.findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId)
    val returnToCustodyDate = calculationTransactionalService.findReturnToCustodyDateFromCalculation(calculationRequestId)
    val calculation = calculationTransactionalService.findCalculationResults(calculationRequestId)
    val userInput = calculationTransactionalService.findUserInput(calculationRequestId)
    val sourceData = PrisonApiSourceData(sentencesAndOffences, prisonerDetails, adjustments, returnToCustodyDate)

    return calculationTransactionalService.calculateWithDiagram(bookingService.getBooking(sourceData, userInput), calculation)
  }
  @PostMapping(value = ["/{prisonerId}/validate"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Validates that the data for the given prisoner in NOMIS can be used to calculate a release date",
    description = "This endpoint will validate that the data for the given prisoner in NOMIS can be supported by the " +
      "calculate release dates engine",
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

  fun validate(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @RequestBody
    calculationUserInputs: CalculationUserInputs?
  ): ResponseEntity<ValidationMessages?> {
    log.info("Request received to validate $prisonerId")
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId)
    var validationMessages = validationService.validate(sourceData)
    if (validationMessages.type == ValidationType.VALID) {
      try {
        val booking = bookingService.getBooking(sourceData, calculationUserInputs)
        calculationService.calculateReleaseDates(booking)
      } catch (validationException: CrdCalculationValidationException) {
        validationMessages = ValidationMessages(
          ValidationType.VALIDATION,
          listOf(ValidationMessage(validationException.message, validationException.validation, arguments = validationException.arguments))
        )
      }
    }

    return if (validationMessages.type == ValidationType.VALID) {
      ResponseEntity(HttpStatus.NO_CONTENT)
    } else {
      ResponseEntity(validationMessages, HttpStatus.OK)
    }
  }

  @GetMapping(value = ["/sentence-and-offences/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get sentences and offences for a calculationRequestId",
    description = "This endpoint will return the sentences and offences based on a calculationRequestId",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId")
    ]
  )
  fun getSentencesAndOffence(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long
  ): List<SentenceAndOffences> {
    log.info("Request received to get sentences and offences from $calculationRequestId calculation")
    return calculationTransactionalService.findSentenceAndOffencesFromCalculation(calculationRequestId)
  }

  @GetMapping(value = ["/prisoner-details/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get prisoner details for a calculationRequestId",
    description = "This endpoint will return the prisoner details based on a calculationRequestId",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId")
    ]
  )
  fun getPrisonerDetails(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long
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
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId")
    ]
  )
  fun getReturnToCustodyDate(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long
  ): ReturnToCustodyDate {
    log.info("Request received to get return to custody date from $calculationRequestId calculation")
    val returnToCustodyDate = calculationTransactionalService.findReturnToCustodyDateFromCalculation(calculationRequestId)
      ?: throw EntityNotFoundException("No return to custody date exists for calculationRequestId $calculationRequestId ")
    return returnToCustodyDate!!
  }

  @GetMapping(value = ["/calculation-user-input/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get user input for a calculationRequestId",
    description = "This endpoint will return the user input based on a calculationRequestId",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId")
    ]
  )
  fun getCalculationInput(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long
  ): CalculationUserInputs {
    log.info("Request received to get user input from $calculationRequestId calculation")
    return calculationTransactionalService.findUserInput(calculationRequestId)
      ?: throw EntityNotFoundException("No user inputs for calculationRequestId $calculationRequestId ")
  }

  @GetMapping(value = ["/adjustments/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get adjustments for a calculationRequestId",
    description = "This endpoint will return the adjustments based on a calculationRequestId",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "RELEASE_DATES_CALCULATOR")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No calculation exists for this calculationRequestId")
    ]
  )
  fun get(
    @Parameter(required = true, example = "123456", description = "The calculationRequestId of the calculation")
    @PathVariable("calculationRequestId")
    calculationRequestId: Long
  ): BookingAndSentenceAdjustments {
    log.info("Request received to get booking and sentence adjustments from $calculationRequestId calculation")
    return calculationTransactionalService.findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId)
  }

  @GetMapping(value = ["/{prisonerId}/user-questions"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Return which sentences and offences may be considered for different calculation rules",
    description = "This endpoint will return which sentences and offences may be considered for different calculation rules." +
      "We will have to ask the user for clarification if any of the rules apply beacuse we cannot trust input data from NOMIS",
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
  fun getCalculationUserQuestions(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): CalculationUserQuestions {
    log.info("Request received to get sentences which require user input $prisonerId")
    val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
    val sentenceAndOffences = prisonService.getSentencesAndOffences(prisonerDetails.bookingId)
    return calculationUserQuestionService.getQuestionsForSentences(prisonerDetails, sentenceAndOffences)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
