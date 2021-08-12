package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

class CalculationServiceTest {
  private val jsonTransformation = JsonTransformation()
  private val sentenceCalculationService = SentenceCalculationService()
  private val sentencesExtractionService = SentencesExtractionService()
  private val sentenceCombinationService = SentenceCombinationService(sentenceCalculationService)
  private val bookingCalculationService = BookingCalculationService(
    sentenceCalculationService,
    sentencesExtractionService,
    sentenceCombinationService
  )
  private val calculationService =
    CalculationService(bookingCalculationService)

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/psi_examples.csv"], numLinesToSkip = 1)
  fun `Test PSI Example`(exampleNumber: String) {
    log.info("Testing PSI example $exampleNumber")
    val booking = jsonTransformation.loadBooking("psi-examples/$exampleNumber")
    val bookingCalculation = calculationService.calculate(booking)
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
