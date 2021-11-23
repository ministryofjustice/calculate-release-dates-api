package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.JsonNode
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.vladmihalcea.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.jwt.Jwt
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.AuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.Optional
import java.util.UUID
import javax.persistence.EntityNotFoundException

class CalculationServiceTest {
  private val jsonTransformation = JsonTransformation()
  private val sentenceCalculationService = SentenceCalculationService()
  private val sentencesExtractionService = SentencesExtractionService()
  private val sentenceIdentificationService = SentenceIdentificationService()
  private val sentenceCombinationService = SentenceCombinationService(
    sentenceIdentificationService
  )
  private val concurrentSentenceCombinationService = ConcurrentSentenceCombinationService(
    sentenceCombinationService
  )
  private val consecutiveSentenceCombinationService = ConsecutiveSentenceCombinationService(
    sentenceCombinationService
  )
  private val bookingCalculationService = BookingCalculationService(
    sentenceCalculationService,
    sentenceIdentificationService,
    consecutiveSentenceCombinationService,
    concurrentSentenceCombinationService
  )
  private val bookingExtractionService = BookingExtractionService(
    sentencesExtractionService
  )

  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val prisonApiClient = mock<PrisonApiClient>()
  private val domainEventPublisher = mock<DomainEventPublisher>()

  private val calculationService =
    CalculationService(
      bookingCalculationService,
      bookingExtractionService,
      calculationRequestRepository,
      calculationOutcomeRepository,
      TestUtil.objectMapper(),
      prisonApiClient,
      domainEventPublisher,
    )

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/calculation-service-examples.csv"], numLinesToSkip = 1)
  fun `Test Example`(exampleType: String, exampleNumber: String) {
    log.info("Testing example $exampleType/$exampleNumber")
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST)
    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList()))
    )
    val booking = jsonTransformation.loadBooking("$exampleType/$exampleNumber")
    val bookingCalculation = calculationService.calculate(booking, PRELIMINARY)
    log.info(
      "Example $exampleType/$exampleNumber outcome BookingCalculation: {}",
      bookingCalculation
    )
    assertEquals(
      jsonTransformation.loadBookingCalculation("$exampleType/$exampleNumber"),
      bookingCalculation
    )
  }

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/calculation-breakdown-examples.csv"], numLinesToSkip = 1)
  fun `Test UX Example Breakdowns`(exampleType: String, exampleNumber: String) {
    log.info("Testing example $exampleType/$exampleNumber")
    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList()))
    )
    val booking = jsonTransformation.loadBooking("$exampleType/$exampleNumber")
    val calculationBreakdown = calculationService.calculateWithBreakdown(booking)

    log.info(
      "Example $exampleType/$exampleNumber outcome CalculationBreakdown: {}",
      TestUtil.objectMapper().writeValueAsString(calculationBreakdown)
    )

    assertEquals(
      jsonTransformation.loadCalculationBreakdown("$exampleType/$exampleNumber"),
      calculationBreakdown
    )
  }

  @Test
  fun `Test fetching calculation results by requestId`() {
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES
      )
    )

    val bookingCalculation = calculationService.findCalculationResults(CALCULATION_REQUEST_ID)

    assertEquals(
      bookingCalculation,
      BOOKING_CALCULATION
    )
  }

  @Test
  fun `Test validation of a confirm calculation request fails if the booking data has changed since the PRELIM calc`() {
    whenever(
      calculationRequestRepository.findByIdAndCalculationStatus(
        CALCULATION_REQUEST_ID,
        PRELIMINARY.name
      )
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES
      )
    )

    val exception = assertThrows<PreconditionFailedException> {
      calculationService.validateConfirmationRequest(CALCULATION_REQUEST_ID, BOOKING)
    }
    assertThat(exception)
      .isInstanceOf(PreconditionFailedException::class.java)
      .withFailMessage("The booking data used for the preliminary calculation has changed")
  }

  @Test
  fun `Test validation of a confirm calculation request fails if there is no PRELIM calc`() {
    whenever(
      calculationRequestRepository.findByIdAndCalculationStatus(
        CALCULATION_REQUEST_ID,
        PRELIMINARY.name
      )
    ).thenReturn(Optional.empty())

    val exception = assertThrows<EntityNotFoundException> {
      calculationService.validateConfirmationRequest(CALCULATION_REQUEST_ID, BOOKING)
    }
    assertThat(exception)
      .isInstanceOf(EntityNotFoundException::class.java)
      .withFailMessage("No preliminary calculation exists for calculationRequestId $CALCULATION_REQUEST_ID")
  }

  @Test
  fun `Test validation succeeds if the PRELIMINARY calculation matches the one being confirmed`() {
    whenever(
      calculationRequestRepository.findByIdAndCalculationStatus(
        CALCULATION_REQUEST_ID,
        PRELIMINARY.name
      )
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)
      )
    )

    assertDoesNotThrow { calculationService.validateConfirmationRequest(CALCULATION_REQUEST_ID, BOOKING) }
  }

  @Test
  fun `Test can find booking from a calculation request id`() {
    whenever(
      calculationRequestRepository.findById(
        CALCULATION_REQUEST_ID
      )
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)
      )
    )

    val booking = calculationService.getBooking(CALCULATION_REQUEST_ID)

    assertThat(booking).isNotNull
  }

  @Test
  fun `Test that write to NOMIS and publishing event succeeds`() {
    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList()))
    )
    whenever(
      calculationRequestRepository.findById(
        CALCULATION_REQUEST_ID
      )
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)
      )
    )

    calculationService.writeToNomisAndPublishEvent(PRISONER_ID, BOOKING, BOOKING_CALCULATION)

    verify(prisonApiClient).postReleaseDates(
      BOOKING_ID,
      UPDATE_OFFENDER_DATES.copy(
        keyDates = OffenderKeyDates(
          conditionalReleaseDate = THIRD_FEB_2021,
          sentenceExpiryDate = THIRD_FEB_2021,
          effectiveSentenceEndDate = THIRD_FEB_2021,
          sentenceLength = "0/0/0"
        )
      )
    )
    verify(domainEventPublisher).publishReleaseDateChange(PRISONER_ID, BOOKING_ID)
  }

  @Test
  fun `Test that exception with correct message is thrown if write to NOMIS fails `() {
    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList()))
    )
    whenever(
      calculationRequestRepository.findById(
        CALCULATION_REQUEST_ID
      )
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)
      )
    )
    whenever(
      prisonApiClient.postReleaseDates(any(), any())
    ).thenThrow(EntityNotFoundException("test ex"))

    val exception = assertThrows<EntityNotFoundException> {
      calculationService.writeToNomisAndPublishEvent(PRISONER_ID, BOOKING, BOOKING_CALCULATION)
    }

    assertThat(exception)
      .isInstanceOf(EntityNotFoundException::class.java)
      .withFailMessage(
        "Writing release dates to NOMIS failed for prisonerId $PRISONER_ID " +
          "and bookingId $BOOKING_ID"
      )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val USERNAME = "user1"
    private const val PRISONER_ID = "A1234AJ"
    private const val BOOKING_ID = 12345L
    private const val CALCULATION_REQUEST_ID = 123456L
    private val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val FAKE_TOKEN: Jwt = Jwt
      .withTokenValue("123")
      .header("header1", "value1")
      .claim("claim1", "value1")
      .build()
    private val CALCULATION_REFERENCE: UUID = UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5")
    private val THIRD_FEB_2021 = LocalDate.of(2021, 2, 3)
    private val CALCULATION_OUTCOME_CRD = CalculationOutcome(
      calculationDateType = CRD.name,
      outcomeDate = THIRD_FEB_2021,
      calculationRequestId = CALCULATION_REQUEST_ID
    )
    private val CALCULATION_OUTCOME_SED = CalculationOutcome(
      calculationDateType = SED.name,
      outcomeDate = THIRD_FEB_2021,
      calculationRequestId = CALCULATION_REQUEST_ID
    )
    val CALCULATION_REQUEST = CalculationRequest(
      calculationReference = CALCULATION_REFERENCE, prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID
    )

    val INPUT_DATA: JsonNode =
      JacksonUtil.toJsonNode(
        "{ \"offender\":{ \"reference\":\"A1234AJ\", \"name\":\"John Doe\", \"dateOfBirth\"" +
          ":\"1980-01-01\" }, \"sentences\":[{\"caseSequence\":1,\"lineSequence\":2,\"offence\":{\"committedAt\":\"2021-02-03\",\"" +
          "isScheduleFifteen\":false},\"duration\":{\"durationElements\":{\"DAYS\":0,\"WEEKS\":0,\"" +
          "MONTHS\":0,\"YEARS\":5}},\"sentencedAt\":\"2021-02-03\"," +
          "\"identifier\":\"5ac7a5ae-fa7b-4b57-a44f-8eddde24f5fa\",\"consecutiveSentenceUUIDs\":[]," +
          "\"sentenceParts\":[]}], \"adjustments\":{}, \"bookingId\":12345 }"
      )

    val CALCULATION_REQUEST_WITH_OUTCOMES = CalculationRequest(
      id = CALCULATION_REQUEST_ID,
      calculationReference = CALCULATION_REFERENCE, prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      calculationOutcomes = listOf(CALCULATION_OUTCOME_CRD, CALCULATION_OUTCOME_SED),
      inputData = JacksonUtil.toJsonNode(
        "{" + "\"offender\":{" + "\"reference\":\"ABC123D\"," +
          "\"name\":\"AN.Other\"," + "\"dateOfBirth\":\"1970-03-03\"" + "}," + "\"sentences\":[" +
          "{" + "\"caseSequence\":1," + "\"lineSequence\":2," +
          "\"offence\":{" + "\"committedAt\":\"2013-09-19\"" + "}," + "\"duration\":{" +
          "\"durationElements\":{" + "\"YEARS\":2" + "}" + "}," + "\"sentencedAt\":\"2013-09-21\"" + "}" + "]" + "}"
      ),
    )

    val BOOKING_CALCULATION = BookingCalculation(
      dates = mutableMapOf(CRD to CALCULATION_OUTCOME_CRD.outcomeDate, SED to THIRD_FEB_2021),
      calculationRequestId = CALCULATION_REQUEST_ID
    )

    val UPDATE_OFFENDER_DATES = UpdateOffenderDates(
      calculationUuid = CALCULATION_REQUEST_WITH_OUTCOMES.calculationReference,
      submissionUser = USERNAME,
      keyDates = OffenderKeyDates(
        conditionalReleaseDate = THIRD_FEB_2021,
        licenceExpiryDate = THIRD_FEB_2021,
        sentenceExpiryDate = THIRD_FEB_2021,
        effectiveSentenceEndDate = THIRD_FEB_2021,
        sentenceLength = "11/0/0"
      )
    )

    private val OFFENDER = Offender(PRISONER_ID, "John Doe", LocalDate.of(1980, 1, 1))

    private val SENTENCE = Sentence(
      sentencedAt = THIRD_FEB_2021,
      duration = FIVE_YEAR_DURATION,
      offence = Offence(committedAt = THIRD_FEB_2021),
      identifier = UUID.fromString("5ac7a5ae-fa7b-4b57-a44f-8eddde24f5fa"),
      caseSequence = 1,
      lineSequence = 2
    )

    val BOOKING = Booking(OFFENDER, mutableListOf(SENTENCE), mutableMapOf(), BOOKING_ID)
  }
}
