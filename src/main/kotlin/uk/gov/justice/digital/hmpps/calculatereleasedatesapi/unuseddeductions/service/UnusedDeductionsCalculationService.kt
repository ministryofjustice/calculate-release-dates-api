package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UnusedDeductionCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationSourceDataService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SourceDataLookupOptions
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

  fun calculate(adjustments: List<AdjustmentDto>, offenderNo: String): UnusedDeductionCalculationResponse = calculateDetailed(adjustments, offenderNo).response

  fun calculateDetailed(adjustments: List<AdjustmentDto>, offenderNo: String): UnusedDeductionsCalculationDetail {
    val prisoner = prisonService.getOffenderDetail(offenderNo)
    val sourceData = sourceDataService.getCalculationSourceData(prisoner, SourceDataLookupOptions.default())
      .copy(bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = adjustments.map { it.copy(effectiveDays = it.days) }))

    val calculationUserInputs = CalculationUserInputs(useOffenceIndicators = true)

    val validationMessages = validationService.validate(sourceData, calculationUserInputs, ValidationOrder.INVALID)
      .filterNot { listOf(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL, ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND, ValidationCode.RELEASE_DATE_BEFORE_SENTENCE_DATE).contains(it.code) }
    if (validationMessages.isNotEmpty()) {
      return UnusedDeductionsCalculationDetail(UnusedDeductionCalculationResponse(null, validationMessages), null, null, prisoner)
    }

    val booking = bookingService.getBooking(sourceData)
    val result = calculationService.calculateReleaseDates(booking, calculationUserInputs, calculateSentenceLevelDates = false)

    return calculateDetailed(result, prisoner)
  }

  fun calculate(result: CalculationOutput): UnusedDeductionCalculationResponse = calculateDetailed(result, null).response

  private fun calculateDetailed(result: CalculationOutput, prisoner: PrisonerDetails?): UnusedDeductionsCalculationDetail {
    val sentences = result.sentenceGroup.last().sentences
    val releaseDateTypes = listOf(ReleaseDateType.CRD, ReleaseDateType.ARD, ReleaseDateType.MTD)
    val calculationResult = result.calculationResult
    val releaseDate = calculationResult.dates.filter { releaseDateTypes.contains(it.key) }.minOfOrNull { it.value }
    val deductions = sentences.maxOfOrNull { it.sentenceCalculation.adjustments.deductions } ?: 0
    var custodialDays: Long? = null
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

      custodialDays = maxDeductions
      max(0, deductions - maxDeductions)
    } else {
      0
    }
    return UnusedDeductionsCalculationDetail(UnusedDeductionCalculationResponse(unusedDeductions, emptyList()), custodialDays, deductions, prisoner)
  }
}

/**
 * The unused deductions response along with the intermediate values used to derive it.
 * custodialDays is the number of days of custody that deductions can be applied against (null when no release date could be determined).
 */
data class UnusedDeductionsCalculationDetail(
  val response: UnusedDeductionCalculationResponse,
  val custodialDays: Long?,
  val totalDeductions: Long?,
  val prisoner: PrisonerDetails?,
)
