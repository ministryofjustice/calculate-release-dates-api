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
  private val offenderSentenceProfileCalculationService =
    OffenderSentenceProfileCalculationService(sentenceCalculationService, sentencesExtractionService)
  private val calculationService =
    CalculationService(offenderSentenceProfileCalculationService)

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/psi_examples.csv"], numLinesToSkip = 1)
  fun `Test PSI Example`(exampleNumber: String) {
    log.info("Testing PSI example $exampleNumber")
    val overallCalculation = jsonTransformation.loadOffenderSentenceProfile("psi-examples/$exampleNumber")
    val offenderSentenceProfileCalculation = calculationService.calculate(overallCalculation)
    assertEquals(
      jsonTransformation.loadOffenderSentenceProfileCalculation(exampleNumber),
      offenderSentenceProfileCalculation
    )
    log.info(
      "Example $exampleNumber outcome OffenderSentenceProfileCalculation: {}",
      offenderSentenceProfileCalculation
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
