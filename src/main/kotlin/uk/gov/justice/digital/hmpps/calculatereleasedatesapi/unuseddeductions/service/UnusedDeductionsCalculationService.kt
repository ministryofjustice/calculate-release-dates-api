package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UnusedDeductionCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
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
) {

  fun calculate(adjustments: List<AdjustmentDto>, offenderNo: String): UnusedDeductionCalculationResponse {
    val prisoner = prisonService.getOffenderDetail(offenderNo)
    val sourceData = prisonService.getPrisonApiSourceData(prisoner, InactiveDataOptions.default()).copy(bookingAndSentenceAdjustments = useAdjustmentsFromAdjustmentsApi(adjustments))

    val calculationUserInputs = CalculationUserInputs(useOffenceIndicators = true)

    var validationMessages = validationService.validateBeforeCalculation(sourceData, calculationUserInputs)
    if (validationMessages.isNotEmpty()) {
      return UnusedDeductionCalculationResponse(null, validationMessages)
    }
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
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

  private fun mapToBookingAdjustmentType(adjustmentType: AdjustmentDto.AdjustmentType): BookingAdjustmentType? {
    return when (adjustmentType) {
      AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE -> BookingAdjustmentType.UNLAWFULLY_AT_LARGE
      AdjustmentDto.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED -> BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED
      AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED -> BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED
      AdjustmentDto.AdjustmentType.LAWFULLY_AT_LARGE -> BookingAdjustmentType.LAWFULLY_AT_LARGE
      AdjustmentDto.AdjustmentType.SPECIAL_REMISSION -> BookingAdjustmentType.SPECIAL_REMISSION
      else -> null
    }
  }
  private fun mapToSentenceAdjustmentType(adjustmentType: AdjustmentDto.AdjustmentType): SentenceAdjustmentType? {
    return when (adjustmentType) {
      AdjustmentDto.AdjustmentType.REMAND -> SentenceAdjustmentType.REMAND
      AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS -> SentenceAdjustmentType.UNUSED_REMAND
      AdjustmentDto.AdjustmentType.TAGGED_BAIL -> SentenceAdjustmentType.TAGGED_BAIL
      AdjustmentDto.AdjustmentType.CUSTODY_ABROAD -> SentenceAdjustmentType.TIME_SPENT_IN_CUSTODY_ABROAD
      AdjustmentDto.AdjustmentType.APPEAL_APPLICANT -> SentenceAdjustmentType.TIME_SPENT_AS_AN_APPEAL_APPLICANT
      else -> null
    }
  }

  private fun useAdjustmentsFromAdjustmentsApi(adjustments: List<AdjustmentDto>): BookingAndSentenceAdjustments {
    return BookingAndSentenceAdjustments(
      bookingAdjustments = adjustments.filter { mapToBookingAdjustmentType(it.adjustmentType) != null }
        .map { BookingAdjustment(active = true, fromDate = it.fromDate!!, toDate = it.toDate, numberOfDays = it.effectiveDays!!, type = mapToBookingAdjustmentType(it.adjustmentType)!!) },
      sentenceAdjustments = adjustments.filter { mapToSentenceAdjustmentType(it.adjustmentType) != null }
        .map { SentenceAdjustment(active = true, fromDate = it.fromDate, toDate = it.toDate, numberOfDays = it.days!!, sentenceSequence = it.sentenceSequence!!, type = mapToSentenceAdjustmentType(it.adjustmentType)!!) },

    )
  }
}
