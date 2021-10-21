package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.jwt.Jwt
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.AuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

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
    sentencesExtractionService,
    consecutiveSentenceCombinationService,
    concurrentSentenceCombinationService
  )

  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()

  private val calculationService =
    CalculationService(
      bookingCalculationService,
      calculationRequestRepository,
      calculationOutcomeRepository
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

  @Test
  fun `Test fetching calculation results by requestId`() {
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(CALCULATION_REQUEST))

    val bookingCalculation = calculationService.findCalculationResults(CALCULATION_REQUEST_ID)

    assertEquals(
      bookingCalculation,
      BOOKING_CALCULATION
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val USERNAME = "user1"
    private const val PRISONER_ID = "A1234AJ"
    private const val BOOKING_ID = 12345L
    private const val CALCULATION_REQUEST_ID = 123456L
    val FAKE_TOKEN: Jwt = Jwt
      .withTokenValue("123")
      .header("header1", "value1")
      .claim("claim1", "value1")
      .build()
    private val CALCULATION_REFERENCE: UUID = UUID.randomUUID()
    private val CALCULATION_OUTCOME = CalculationOutcome(
      calculationDateType = SentenceType.CRD.name,
      outcomeDate = LocalDate.of(2021, 2, 3),
      calculationRequestId = CALCULATION_REQUEST_ID
    )
    val CALCULATION_REQUEST = CalculationRequest(
      id = 999919,
      calculationReference = CALCULATION_REFERENCE, prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      calculationOutcomes = listOf(CALCULATION_OUTCOME)
    )

    val BOOKING_CALCULATION = BookingCalculation(
      dates = mutableMapOf(SentenceType.CRD to CALCULATION_OUTCOME.outcomeDate),
      calculationRequestId = CALCULATION_REQUEST.id
    )
  }
}
