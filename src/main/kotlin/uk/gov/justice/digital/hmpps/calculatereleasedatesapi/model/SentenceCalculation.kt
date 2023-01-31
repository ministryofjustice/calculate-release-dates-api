package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_SERVED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.LICENSE_UNUSED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RELEASE_UNUSED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ConsecutiveSentenceAggregator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

data class SentenceCalculation(
  var sentence: CalculableSentence, // values here are used to store working values
  val numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToDeterminateReleaseDateDouble: Double,
  val numberOfDaysToDeterminateReleaseDate: Int,
  val unadjustedExpiryDate: LocalDate,
  val unadjustedDeterminateReleaseDate: LocalDate,
  val numberOfDaysToPostRecallReleaseDate: Int?,
  val unadjustedPostRecallReleaseDate: LocalDate?,
  val calculateErsed: Boolean,
  val adjustments: Adjustments,
  // This is the date of the latest release of a sentence that runs concurrently to this.
  var latestConcurrentRelease: LocalDate = sentence.sentencedAt,
  // This is the date of the latest determinate release of a sentence that runs concurrently to this.
  var latestConcurrentDeterminateRelease: LocalDate = sentence.sentencedAt,
  var adjustmentsAfter: LocalDate? = null,
  val returnToCustodyDate: LocalDate? = null,
  val numberOfDaysToParoleEligibilityDate: Long? = null,

) {

  fun getAdjustmentBeforeSentence(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentRelease,
      adjustmentsAfter = adjustmentsAfter
    )
  }

  fun getDeterminateAdjustmentBeforeSentence(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentDeterminateRelease,
      adjustmentsAfter = adjustmentsAfter
    )
  }

  fun getDeterminateAdjustmentsAfterSentenceAtDate(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentDeterminateRelease,
      adjustmentsAfter = if (adjustmentsAfter != null) adjustmentsAfter else sentence.sentencedAt.minusDays(1)
    )
  }

  fun getAdjustmentsAfterSentenceAtDate(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentRelease,
      adjustmentsAfter = if (adjustmentsAfter != null) adjustmentsAfter else sentence.sentencedAt.minusDays(1)
    )
  }

  fun getAdjustmentDuringSentence(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = releaseDateWithoutAwarded,
      adjustmentsAfter = adjustmentsAfter
    )
  }

  fun isImmediateRelease(): Boolean {
    return sentence.sentencedAt == adjustedDeterminateReleaseDate
  }

  val calculatedDeterminateTotalDeductedDays: Int
    get() {
      if (sentence is AFineSentence && sentence.offence.isCivilOffence()) {
        return 0
      }
      val adjustmentTypes: Array<AdjustmentType> = if (!sentence.isRecall()) {
        arrayOf(REMAND, TAGGED_BAIL)
      } else {
        arrayOf(RECALL_REMAND, RECALL_TAGGED_BAIL)
      }
      return getDeterminateAdjustmentBeforeSentence(*adjustmentTypes)
    }

  val calculatedTotalDeductedDays: Int get() {
    if (sentence is AFineSentence && sentence.offence.isCivilOffence()) {
      return 0
    }
    val adjustmentTypes: Array<AdjustmentType> = if (!sentence.isRecall()) {
      arrayOf(REMAND, TAGGED_BAIL)
    } else {
      arrayOf(RECALL_REMAND, RECALL_TAGGED_BAIL)
    }
    return getAdjustmentBeforeSentence(*adjustmentTypes)
  }

  val calculatedDeterminateTotalAddedDays: Int get() {
    return getDeterminateAdjustmentsAfterSentenceAtDate(UNLAWFULLY_AT_LARGE)
  }

  val calculatedTotalAddedDays: Int get() {
    return getAdjustmentsAfterSentenceAtDate(UNLAWFULLY_AT_LARGE)
  }

  fun getTotalAddedDaysAfter(after: LocalDate): Int {
    return adjustments.getOrZero(UNLAWFULLY_AT_LARGE, adjustmentsBefore = latestConcurrentRelease, adjustmentsAfter = after)
  }

  val calculatedFixedTermRecallAddedDays: Int get() {
    return adjustments.getOrZero(UNLAWFULLY_AT_LARGE, adjustmentsBefore = latestConcurrentRelease, adjustmentsAfter = returnToCustodyDate)
  }

  val calculatedTotalAwardedDays: Int get() {
    if (sentence is AFineSentence) {
      return 0
    }
    return max(
      0,
      getAdjustmentDuringSentence(ADDITIONAL_DAYS_AWARDED) -
        getAdjustmentDuringSentence(RESTORATION_OF_ADDITIONAL_DAYS_AWARDED, ADDITIONAL_DAYS_SERVED)

    )
  }

  val calculatedUnusedReleaseAda: Int get() {
    return getAdjustmentDuringSentence(RELEASE_UNUSED_ADA)
  }

  val calculatedUnusedLicenseAda: Int get() {
    return getAdjustmentDuringSentence(LICENSE_UNUSED_ADA)
  }

  val numberOfDaysToAddToLicenceExpiryDate: Int get() {
    if (!sentence.isRecall() && calculatedTotalDeductedDays >= numberOfDaysToDeterminateReleaseDate) {
      return calculatedTotalDeductedDays - numberOfDaysToDeterminateReleaseDate
    }
    return 0
  }

  val adjustedExpiryDate: LocalDate get() {
    return unadjustedExpiryDate
      .minusDays(
        calculatedTotalDeductedDays.toLong()
      ).plusDays(
        calculatedTotalAddedDays.toLong()
      )
  }

  val releaseDateWithoutAwarded: LocalDate get() {
    return unadjustedDeterminateReleaseDate.minusDays(
      calculatedDeterminateTotalDeductedDays.toLong()
    ).plusDays(
      calculatedDeterminateTotalAddedDays.toLong()
    )
  }

  /*
    Determinate release date that is not "capped" by the sentence date or expiry date.
   */
  val adjustedUncappedDeterminateReleaseDate: LocalDate get() {
    return releaseDateWithoutAwarded.plusDays(
      calculatedTotalAwardedDays.toLong()
    )
  }

  val adjustedDeterminateReleaseDate: LocalDate get() {
    val date = adjustedUncappedDeterminateReleaseDate.minusDays(calculatedUnusedReleaseAda.toLong())
    return if (date.isAfter(sentence.sentencedAt)) {
      date
    } else {
      sentence.sentencedAt
    }
  }

  val adjustedPostRecallReleaseDate: LocalDate? get() {
    if (sentence.isRecall()) {
      if (sentence.recallType == RecallType.STANDARD_RECALL) {
        return unadjustedPostRecallReleaseDate?.minusDays(
          calculatedTotalDeductedDays.toLong()
        )?.plusDays(
          calculatedTotalAddedDays.toLong()
        )
      }
      if (sentence.recallType!!.isFixedTermRecall) {
        // Fixed term recalls only apply adjustments from return to custody date
        val fixedTermRecallRelease = unadjustedPostRecallReleaseDate?.plusDays(
          calculatedFixedTermRecallAddedDays.toLong()
        )
        return minOf(fixedTermRecallRelease!!, expiryDate!!)
      }
    }
    return null
  }

  // Non Parole Date (NPD)
  var numberOfDaysToNonParoleDate: Long = 0
  var nonParoleDate: LocalDate? = null

  private val unadjustedExtendedDeterminateParoleEligibilityDate: LocalDate? get() {
    if (numberOfDaysToParoleEligibilityDate == null) {
      return null
    }
    return sentence.sentencedAt
      .plusDays(numberOfDaysToParoleEligibilityDate)
      .minusDays(1)
  }

  // Parole Eligibility Date (PED). This is only used for EDS, for SDS the PED is the release date.
  val extendedDeterminateParoleEligibilityDate: LocalDate? get() {
    if (unadjustedExtendedDeterminateParoleEligibilityDate == null) {
      return null
    }
    return unadjustedExtendedDeterminateParoleEligibilityDate!!
      .plusDays(calculatedTotalAddedDays.toLong())
      .minusDays(calculatedTotalDeductedDays.toLong())
      .plusDays(calculatedTotalAwardedDays.toLong())
  }

  val earlyReleaseSchemeEligibilityDate: LocalDate? get() {
    return earlyReleaseSchemeEligibilityDateBreakdown?.releaseDate
  }

  val earlyReleaseSchemeEligibilityDateBreakdown: ReleaseDateCalculationBreakdown? get() {
    val ersed = calculateErsed()
    return if (ersed != null && ersed.releaseDate.isBefore(sentence.sentencedAt)) {
      ReleaseDateCalculationBreakdown(releaseDate = sentence.sentencedAt, unadjustedDate = sentence.sentencedAt, rules = setOf(CalculationRule.ERSED_BEFORE_SENTENCE_DATE))
    } else {
      ersed
    }
  }

  private fun calculateErsed(): ReleaseDateCalculationBreakdown? {
    if (calculateErsed && !sentence.isRecall()) {
      if (sentence.calculateErsedFromHalfway()) {
        return calculateErsedFromHalfway()
      }
      if (sentence.calculateErsedFromTwoThirds()) {
        return calculateErsedFromTwoThirds()
      }
    }
    return null
  }

  private fun calculateErsedFromTwoThirds(): ReleaseDateCalculationBreakdown {
    val days = if (sentence is ConsecutiveSentence) {
      ConsecutiveSentenceAggregator((sentence as ConsecutiveSentence).orderedSentences.map { it.custodialDuration() }).calculateDays(sentence.sentencedAt)
    } else {
      val custodialDuration = sentence.custodialDuration()
      custodialDuration.getLengthInDays(sentence.sentencedAt)
    }
    return if (days >= RELEASE_AT_TWO_THIRDS_ERSED_DAYS) {
      val release = extendedDeterminateParoleEligibilityDate ?: releaseDate
      val ersed = release.minusYears(1)
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.ERSED_ONE_YEAR),
        releaseDate = ersed,
        unadjustedDate = unadjustedExtendedDeterminateParoleEligibilityDate ?: unadjustedDeterminateReleaseDate,
        adjustedDays = ChronoUnit.DAYS.between(
          unadjustedDeterminateReleaseDate,
          adjustedDeterminateReleaseDate
        ).toInt(),
        rulesWithExtraAdjustments = mapOf(CalculationRule.ERSED_ONE_YEAR to AdjustmentDuration(-12, ChronoUnit.MONTHS))
      )
    } else {
      val unadjustedErsed =
        sentence.sentencedAt
          .plusDays(ceil(days.toDouble() / 3).toLong())
      val ersed = unadjustedErsed
        .plusDays(calculatedTotalAddedDays.toLong())
        .minusDays(calculatedTotalDeductedDays.toLong())
        .plusDays(calculatedTotalAwardedDays.toLong())
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.ERSED_TWO_THIRDS),
        releaseDate = ersed,
        unadjustedDate = unadjustedErsed,
        adjustedDays = ChronoUnit.DAYS.between(unadjustedErsed, ersed).toInt()
      )
    }
  }

  private fun calculateErsedFromHalfway(): ReleaseDateCalculationBreakdown {
    val days = if (sentence is ConsecutiveSentence) {
      ConsecutiveSentenceAggregator((sentence as ConsecutiveSentence).orderedSentences.map { it.custodialDuration() }).calculateDays(sentence.sentencedAt)
    } else {
      val custodialDuration = sentence.custodialDuration()
      custodialDuration.getLengthInDays(sentence.sentencedAt)
    }
    return if (days >= RELEASE_AT_HALFWAY_ERSED_DAYS) {
      val release = extendedDeterminateParoleEligibilityDate ?: releaseDate
      val ersed = release.minusYears(1)
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.ERSED_ONE_YEAR),
        releaseDate = ersed,
        unadjustedDate = unadjustedExtendedDeterminateParoleEligibilityDate ?: unadjustedDeterminateReleaseDate,
        adjustedDays = ChronoUnit.DAYS.between(
          unadjustedDeterminateReleaseDate,
          adjustedDeterminateReleaseDate
        ).toInt(),
        rulesWithExtraAdjustments = mapOf(CalculationRule.ERSED_ONE_YEAR to AdjustmentDuration(-12, ChronoUnit.MONTHS))
      )
    } else {
      val unadjustedErsed =
        sentence.sentencedAt
          .plusDays(ceil(days.toDouble() / 4).toLong())
      val ersed = unadjustedErsed
        .plusDays(calculatedTotalAddedDays.toLong())
        .minusDays(calculatedTotalDeductedDays.toLong())
        .plusDays(calculatedTotalAwardedDays.toLong())
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.ERSED_HALFWAY),
        releaseDate = ersed,
        unadjustedDate = unadjustedErsed,
        adjustedDays = ChronoUnit.DAYS.between(unadjustedErsed, ersed).toInt()
      )
    }
  }
  // Licence Expiry Date (LED)
  var numberOfDaysToLicenceExpiryDate: Long = 0
  private var _licenceExpiryDate: LocalDate? = null
  var licenceExpiryDate: LocalDate?
    get() {
      return if (sentence.releaseDateTypes.initialTypes().contains(ReleaseDateType.SLED)) {
        expiryDate
      } else {
        _licenceExpiryDate
      }
    }
    set(value) {
      _licenceExpiryDate = value
    }

  //  Home Detention Curfew Eligibility Date(HDCED)
  var numberOfDaysToHomeDetentionCurfewEligibilityDate: Long = 0
  var homeDetentionCurfewEligibilityDate: LocalDate? = null
  var breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown> = mutableMapOf()

  // Notional Conditional Release Date (NCRD)
  var numberOfDaysToNotionalConditionalReleaseDate: Long = 0
  var notionalConditionalReleaseDate: LocalDate? = null

  val releaseDate: LocalDate get() {
    return if (sentence.isRecall()) {
      adjustedPostRecallReleaseDate!!
    } else {
      adjustedDeterminateReleaseDate
    }
  }

  val releaseDateWithoutAdditions: LocalDate get() {
    return if (sentence.recallType == RecallType.STANDARD_RECALL) {
      releaseDate.minusDays(calculatedTotalAddedDays.toLong())
    } else if (sentence.recallType?.isFixedTermRecall == true) {
      releaseDate.minusDays(calculatedFixedTermRecallAddedDays.toLong())
    } else {
      releaseDate.minusDays(calculatedTotalAddedDays.toLong()).minusDays(calculatedTotalAwardedDays.toLong())
    }
  }

  val expiryDate: LocalDate? get() {
    if (sentence.releaseDateTypes.initialTypes().contains(ReleaseDateType.SLED) || sentence.releaseDateTypes.initialTypes().contains(ReleaseDateType.SED)) {
      return adjustedExpiryDate
    }
    return null
  }
  var topUpSupervisionDate: LocalDate? = null
  var isReleaseDateConditional: Boolean = false

  fun buildString(releaseDateTypes: ReleaseDateTypes): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val expiryDateType = if (releaseDateTypes.contains(ReleaseDateType.SLED)) "SLED" else "SED"
    val releaseDateType = if (releaseDateTypes.contains(ReleaseDateType.ARD)) "ARD"
    else if (releaseDateTypes.contains(ReleaseDateType.CRD)) "CRD"
    else "PED"

    return "Date of $expiryDateType\t:\t${unadjustedExpiryDate.format(formatter)}\n" +
      "Number of days to $releaseDateType\t:\t${numberOfDaysToDeterminateReleaseDate}\n" +
      "Date of $releaseDateType\t:\t${unadjustedDeterminateReleaseDate.format(formatter)}\n" +
      "Total number of days of deducted (remand / tagged bail)\t:" +
      "\t${calculatedTotalDeductedDays}\n" +
      "Total number of days of added (UAL) \t:\t${calculatedTotalAddedDays}\n" +
      "Total number of days of awarded (ADA / RADA) \t:\t${calculatedTotalAwardedDays}\n" +

      "Total number of days to Licence Expiry Date (LED)\t:\t${numberOfDaysToLicenceExpiryDate}\n" +
      "LED\t:\t${licenceExpiryDate?.format(formatter)}\n" +

      "Number of days to Non Parole Date (NPD)\t:\t${numberOfDaysToNonParoleDate}\n" +
      "Non Parole Date (NPD)\t:\t${nonParoleDate?.format(formatter)}\n" +

      "Number of days to Home Detention Curfew Eligibility Date (HDCED)\t:\t" +
      "${numberOfDaysToHomeDetentionCurfewEligibilityDate}\n" +
      "Home Detention Curfew Eligibility Date (HDCED)\t:\t" +
      "${homeDetentionCurfewEligibilityDate?.format(formatter)}\n" +

      "Effective $expiryDateType\t:\t${expiryDate?.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${releaseDate.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${topUpSupervisionDate?.format(formatter)}\n"
  }

  companion object {
    // These numbers are the point at which the calculated ERSED would be greater than a year before the release.
    const val RELEASE_AT_HALFWAY_ERSED_DAYS = 1463
    const val RELEASE_AT_TWO_THIRDS_ERSED_DAYS = 1097
  }
}
