package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UnusedDeductionCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationSourceDataService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.InactiveDataOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Service
class UnusedDeductionsCalculationService(
  private val prisonService: PrisonService,
  private val calculationService: CalculationService,
  private val validationService: ValidationService,
  private val bookingService: BookingService,
  private val sourceDataService: CalculationSourceDataService,
) {

  fun calculate(adjustments: List<AdjustmentDto>, offenderNo: String): UnusedDeductionCalculationResponse {
    val prisoner = prisonService.getOffenderDetail(offenderNo)
    val sourceData = sourceDataService.getCalculationSourceData(prisoner, InactiveDataOptions.default())
      .copy(bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = adjustments.map { it.copy(effectiveDays = it.days) }))

    val calculationUserInputs = CalculationUserInputs(useOffenceIndicators = true)

    val validationMessages = validationService.validate(sourceData, calculationUserInputs, ValidationOrder.INVALID)
      .filterNot { listOf(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL, ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND).contains(it.code) }
    if (validationMessages.isNotEmpty()) {
      return UnusedDeductionCalculationResponse(null, validationMessages)
    }

    val booking = bookingService.getBooking(sourceData)
    val result = calculationService.calculateReleaseDates(booking, calculationUserInputs)
    val sentences = result.sentenceGroup.last().sentences
    val releaseDateTypes = listOf(ReleaseDateType.CRD, ReleaseDateType.ARD, ReleaseDateType.MTD)
    val calculationResult = result.calculationResult
    val releaseDate = calculationResult.dates.filter { releaseDateTypes.contains(it.key) }.minOfOrNull { it.value }
    val unusedDeductions = if (releaseDate != null) {
      val maxNonDeductionAdjustedReleaseDateSentence = sentences.maxBy {
        it.sentenceCalculation.releaseDateWithoutDeductions
      }
      val maxNonDeductionAdjustedReleaseDate = maxNonDeductionAdjustedReleaseDateSentence.sentenceCalculation.releaseDateWithoutDeductions

      val maxSentenceDate = sentences.maxOf { it.sentencedAt }
      val maxDeductions = if (maxSentenceDate != maxNonDeductionAdjustedReleaseDateSentence.sentencedAt) {
        ChronoUnit.DAYS.between(maxSentenceDate, maxNonDeductionAdjustedReleaseDate)
      } else {
        val allAdjustmentDaysExceptDeductions =
          maxNonDeductionAdjustedReleaseDateSentence.sentenceCalculation.adjustments.ualDuringCustody + maxNonDeductionAdjustedReleaseDateSentence.sentenceCalculation.adjustments.awardedDuringCustody
        maxNonDeductionAdjustedReleaseDateSentence.sentenceCalculation.numberOfDaysToDeterminateReleaseDate + allAdjustmentDaysExceptDeductions
      }

      val deductions = sentences.maxOf { it.sentenceCalculation.adjustments.deductions }
      max(0, deductions - maxDeductions)
    } else {
      0
    }

    return UnusedDeductionCalculationResponse(unusedDeductions, validationMessages)
  }
}
