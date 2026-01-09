package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate

@Service
class PreviouslyRecordedSLEDService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val sourceDataMapper: SourceDataMapper,
) {

  fun findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(prisonerId: String, calculatedSLED: LocalDate): PreviouslyRecordedSLED? {
    val latestCalculationForPreviousSLED = calculationRequestRepository.findLatestCalculationForPreviousSLED(prisonerId)
    return if (latestCalculationForPreviousSLED != null &&
      hasASledOrSedAndLedWithTheSameDate(latestCalculationForPreviousSLED) &&
      didNotHaveUnusedDeduction(latestCalculationForPreviousSLED)
    ) {
      val sledOrSed = requireNotNull(
        latestCalculationForPreviousSLED.calculationOutcomes.find { it.calculationDateType == ReleaseDateType.SLED.name }?.outcomeDate
          ?: latestCalculationForPreviousSLED.calculationOutcomes.find { it.calculationDateType == ReleaseDateType.SED.name }?.outcomeDate,
      )
      if (sledOrSed.isAfter(calculatedSLED)) {
        PreviouslyRecordedSLED(
          calculatedDate = calculatedSLED,
          previouslyRecordedSLEDDate = sledOrSed,
          previouslyRecordedSLEDCalculationRequestId = latestCalculationForPreviousSLED.id(),
        )
      } else {
        null
      }
    } else {
      null
    }
  }

  private fun didNotHaveUnusedDeduction(calculationRequest: CalculationRequest): Boolean = sourceDataMapper.mapAdjustments(calculationRequest).none { it.adjustmentType == AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS && (it.days ?: 0) > 0 }

  private fun hasASledOrSedAndLedWithTheSameDate(latestCalculationForPreviousSLED: CalculationRequest): Boolean = latestCalculationForPreviousSLED.calculationOutcomes.any { it.calculationDateType == ReleaseDateType.SLED.name } || hasSedAndLedWithTheSameDate(latestCalculationForPreviousSLED.calculationOutcomes)

  private fun hasSedAndLedWithTheSameDate(dates: List<CalculationOutcome>): Boolean {
    val sed = dates.find { it.calculationDateType == ReleaseDateType.SED.name }?.outcomeDate
    val led = dates.find { it.calculationDateType == ReleaseDateType.LED.name }?.outcomeDate
    return sed != null && led != null && sed.isEqual(led)
  }
}
