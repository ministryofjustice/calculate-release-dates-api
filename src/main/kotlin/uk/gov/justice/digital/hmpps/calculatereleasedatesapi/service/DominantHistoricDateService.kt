package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import java.time.LocalDate

@Service
class DominantHistoricDateService(private val calculationOutcomeRepository: CalculationOutcomeRepository) {

  fun findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(prisonerId: String, calculatedSLED: LocalDate): PreviouslyRecordedSLED? {
    val dominantDates = calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED(prisonerId, calculatedSLED)
    return dominantDates
      .groupBy { it.calculationRequestId }
      .filter { (_, dates) -> dates.any { it.calculationDateType == ReleaseDateType.SLED.name } || hasSedAndLedWithTheSameDate(dates) }
      .mapNotNull { (calculationRequestId, dates) ->
        val sledOrSed = dates.find { it.calculationDateType == ReleaseDateType.SLED.name } ?: dates.find { it.calculationDateType == ReleaseDateType.SED.name }
        sledOrSed?.let { PreviouslyRecordedSLED(it.outcomeDate!!, calculatedSLED, calculationRequestId) }
      }.maxByOrNull { it.previouslyRecordedSLEDDate }
  }

  private fun hasSedAndLedWithTheSameDate(dates: List<CalculationOutcome>): Boolean {
    val sed = dates.find { it.calculationDateType == ReleaseDateType.SED.name }?.outcomeDate
    val led = dates.find { it.calculationDateType == ReleaseDateType.LED.name }?.outcomeDate
    return sed != null && led != null && sed.isEqual(led)
  }
}
