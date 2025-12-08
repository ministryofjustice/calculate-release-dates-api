package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import kotlin.jvm.optionals.getOrElse

@Service
class PreviouslyRecordedSLEDService(
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val sourceDataMapper: SourceDataMapper,
) {

  fun findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(prisonerId: String, calculatedSLED: LocalDate): PreviouslyRecordedSLED? {
    val potentialDates = calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED(prisonerId, calculatedSLED)
    val bestPossibleDate = potentialDates
      .groupBy { it.calculationRequestId }
      .filter { (_, dates) -> dates.any { it.calculationDateType == ReleaseDateType.SLED.name } || hasSedAndLedWithTheSameDate(dates) }
      .mapNotNull { (calculationRequestId, dates) ->
        val sledOrSed = dates.find { it.calculationDateType == ReleaseDateType.SLED.name } ?: dates.find { it.calculationDateType == ReleaseDateType.SED.name }
        sledOrSed?.let {
          PreviouslyRecordedSLED(
            calculatedDate = calculatedSLED,
            previouslyRecordedSLEDDate = it.outcomeDate!!,
            previouslyRecordedSLEDCalculationRequestId = calculationRequestId,
          )
        }
      }.maxByOrNull { it.previouslyRecordedSLEDDate }
    return if (bestPossibleDate?.let { didNotHaveUnusedDeduction(it) } ?: false) {
      bestPossibleDate
    } else {
      null
    }
  }

  private fun didNotHaveUnusedDeduction(previousSLED: PreviouslyRecordedSLED): Boolean {
    val calcRequest = calculationRequestRepository.findById(previousSLED.previouslyRecordedSLEDCalculationRequestId)
      .getOrElse { throw IllegalStateException("PreviouslyRecordedSLED calculation request id didn't match a calculation request") }

    return sourceDataMapper.mapAdjustments(calcRequest).none { it.adjustmentType == AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS && (it.days ?: 0) > 0 }
  }

  private fun hasSedAndLedWithTheSameDate(dates: List<CalculationOutcome>): Boolean {
    val sed = dates.find { it.calculationDateType == ReleaseDateType.SED.name }?.outcomeDate
    val led = dates.find { it.calculationDateType == ReleaseDateType.LED.name }?.outcomeDate
    return sed != null && led != null && sed.isEqual(led)
  }
}
