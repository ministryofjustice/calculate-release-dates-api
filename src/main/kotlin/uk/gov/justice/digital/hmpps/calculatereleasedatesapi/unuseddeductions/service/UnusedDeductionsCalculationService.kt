package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import kotlin.math.max

@Service
class UnusedDeductionsCalculationService(
  private val prisonService: PrisonService,
  private val calculationService: CalculationService,
  private val validationService: ValidationService,
  private val bookingService: BookingService,
) {

  fun calculate(adjustments: List<AdjustmentServiceAdjustment>, offenderNo: String): Int? {
    val prisoner = prisonService.getOffenderDetail(offenderNo)
    val sourceData = prisonService.getPrisonApiSourceData(prisoner).copy(bookingAndSentenceAdjustments = useAdjustmentsFromAdjustmentsApi(adjustments))

    val calculationUserInputs = CalculationUserInputs(useOffenceIndicators = true)

    var validationMessages = validationService.validateBeforeCalculation(sourceData, calculationUserInputs)
    if (validationMessages.isNotEmpty()) {
      return null
    }
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    validationMessages = validationService.validateBeforeCalculation(booking)
    if (validationMessages.isNotEmpty()) {
      return null
    }

    val result = calculationService.calculateReleaseDates(booking)
    val releaseDateTypes = listOf(ReleaseDateType.CRD, ReleaseDateType.ARD, ReleaseDateType.MTD)
    val calculationResult = result.second
    var releaseDate = calculationResult.dates.filter { releaseDateTypes.contains(it.key) }.minOfOrNull { it.value }
    val releaseDateSentence = result.first.getAllExtractableSentences().find { it.sentenceCalculation.releaseDate == releaseDate }
    val unusedDeductions = if (releaseDateSentence != null) { // The release date will be a PRRD, no unused deductions.
      val maxDeductions =
        releaseDateSentence.sentenceCalculation.numberOfDaysToDeterminateReleaseDate

      val remand = adjustments.filter { it.adjustmentType == AdjustmentServiceAdjustmentType.REMAND }.map { it.daysBetween!! }.reduceOrNull { acc, it -> acc + it } ?: 0
      val taggedBail = adjustments.filter { it.adjustmentType == AdjustmentServiceAdjustmentType.TAGGED_BAIL }.map { it.days!! }.reduceOrNull { acc, it -> acc + it } ?: 0
      val deductions = taggedBail + remand
      max(0, deductions - maxDeductions)
    } else { 0 }
    return unusedDeductions
  }

  private fun mapToBookingAdjustmentType(adjustmentType: AdjustmentServiceAdjustmentType): BookingAdjustmentType? {
    return when (adjustmentType) {
      AdjustmentServiceAdjustmentType.UNLAWFULLY_AT_LARGE -> BookingAdjustmentType.UNLAWFULLY_AT_LARGE
      AdjustmentServiceAdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED -> BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED
      AdjustmentServiceAdjustmentType.ADDITIONAL_DAYS_AWARDED -> BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED
      else -> null
    }
  }

  private fun useAdjustmentsFromAdjustmentsApi(adjustments: List<AdjustmentServiceAdjustment>): BookingAndSentenceAdjustments {
    return BookingAndSentenceAdjustments(
      bookingAdjustments = adjustments.filter { mapToBookingAdjustmentType(it.adjustmentType) != null }
        .map { BookingAdjustment(active = true, fromDate = it.fromDate!!, toDate = it.toDate, numberOfDays = it.effectiveDays!!, type = mapToBookingAdjustmentType(it.adjustmentType)!!) },
      sentenceAdjustments = listOf(),
    )
  }
}
