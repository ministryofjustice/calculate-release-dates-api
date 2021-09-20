package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.nhaarman.mockitokotlin2.mock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

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
    CalculationService(bookingCalculationService, calculationRequestRepository, calculationOutcomeRepository)

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/psi_examples.csv"], numLinesToSkip = 1)
  fun `Test PSI Example`(exampleNumber: String) {
    log.info("Testing PSI example $exampleNumber")
    val booking = jsonTransformation.loadBooking("psi-examples/$exampleNumber")
    val bookingCalculation = calculationService.calculate(booking, PRELIMINARY)
    assertEquals(
      jsonTransformation.loadBookingCalculation(exampleNumber),
      bookingCalculation
    )
    log.info(
      "Example $exampleNumber outcome BookingCalculation: {}",
      bookingCalculation
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
