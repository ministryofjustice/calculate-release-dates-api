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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
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
    val sourceData = sourceDataService.getCalculationSourceData(prisoner, InactiveDataOptions.default()).copy(bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = adjustments))

    val calculationUserInputs = CalculationUserInputs(useOffenceIndicators = true)

    var validationMessages = validationService.validateBeforeCalculation(sourceData, calculationUserInputs)
    if (validationMessages.isNotEmpty()) {
      return UnusedDeductionCalculationResponse(null, validationMessages)
    }
    val booking = bookingService.getBooking(sourceData)
    validationMessages = validationService.validateBeforeCalculation(booking)
    if (validationMessages.isNotEmpty()) {
      return UnusedDeductionCalculationResponse(null, validationMessages)
    }

    val result = calculationService.calculateReleaseDates(booking, calculationUserInputs)
    val releaseDateTypes = listOf(ReleaseDateType.CRD, ReleaseDateType.ARD, ReleaseDateType.MTD)
    val calculationResult = result.calculationResult
    val releaseDate = calculationResult.dates.filter { releaseDateTypes.contains(it.key) }.minOfOrNull { it.value }
    val unusedDeductions = if (releaseDate != null) {
      val maxNonDeductionAdjustedReleaseDateSentence = result.sentences.maxBy {
        it.sentenceCalculation.releaseDateWithoutDeductions
      }
      val maxNonDeductionAdjustedReleaseDate = maxNonDeductionAdjustedReleaseDateSentence.sentenceCalculation.releaseDateWithoutDeductions

      val maxSentenceDate = result.sentences.maxOf { it.sentencedAt }
      val maxDeductions = if (maxSentenceDate != maxNonDeductionAdjustedReleaseDateSentence.sentencedAt) {
        ChronoUnit.DAYS.between(maxSentenceDate, maxNonDeductionAdjustedReleaseDate)
      } else {
        val allAdjustmentDaysExceptDeductions =
          maxNonDeductionAdjustedReleaseDateSentence.sentenceCalculation.adjustments.ualDuringCustody + maxNonDeductionAdjustedReleaseDateSentence.sentenceCalculation.adjustments.awardedDuringCustody
        maxNonDeductionAdjustedReleaseDateSentence.sentenceCalculation.numberOfDaysToDeterminateReleaseDate + allAdjustmentDaysExceptDeductions
      }

      val remand =
        adjustments.filter { it.adjustmentType == AdjustmentDto.AdjustmentType.REMAND }.map { it.days!! }
          .reduceOrNull { acc, it -> acc + it } ?: 0
      val taggedBail =
        adjustments.filter { it.adjustmentType == AdjustmentDto.AdjustmentType.TAGGED_BAIL }.map { it.days!! }
          .reduceOrNull { acc, it -> acc + it } ?: 0
      val deductions = taggedBail + remand
      max(0, deductions - maxDeductions)
    } else {
      0
    }

    validationMessages = validationService.validateBookingAfterCalculation(result, booking)
    return UnusedDeductionCalculationResponse(unusedDeductions, validationMessages)
  }
}
