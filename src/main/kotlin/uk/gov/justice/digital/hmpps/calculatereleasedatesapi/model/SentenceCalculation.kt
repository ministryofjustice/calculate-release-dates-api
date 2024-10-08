package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_SERVED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.LICENSE_UNUSED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class SentenceCalculation(
  // values here are used to store working values
  var sentence: CalculableSentence,
  val numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToDeterminateReleaseDateDouble: Double,
  val numberOfDaysToDeterminateReleaseDate: Int,
  val numberOfDaysToHistoricDeterminateReleaseDate: Int,
  val unadjustedHistoricDeterminateReleaseDate: LocalDate,
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
  var unusedAdaDays: Long = 0,
) {

  fun getAdjustmentBeforeSentence(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentRelease,
      adjustmentsAfter = adjustmentsAfter,
    )
  }

  private fun getDeterminateAdjustmentBeforeSentence(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentDeterminateRelease,
      adjustmentsAfter = adjustmentsAfter,
    )
  }

  private fun getDeterminateAdjustmentsAfterSentenceAtDate(): Int {
    return adjustments.applyPeriodsOfUALIncrementally(
      startDate = sentence.sentencedAt.minusDays(1),
      initialEndDate = latestConcurrentDeterminateRelease,
    )
  }

  private fun getAdjustmentsAfterSentenceAtDate(): Int {
    return adjustments.applyPeriodsOfUALIncrementally(
      startDate = sentence.sentencedAt.minusDays(1),
      initialEndDate = latestConcurrentRelease,
    )
  }

  private fun getAdjustmentDuringSentence(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentDeterminateRelease,
      adjustmentsAfter = adjustmentsAfter,
    )
  }

  fun isImmediateRelease(): Boolean {
    return sentence.sentencedAt == adjustedDeterminateReleaseDate
  }

  fun isImmediateCustodyRelease(): Boolean {
    return sentence.sentencedAt == adjustedDeterminateReleaseDate && getAdjustmentBeforeSentence(
      ADDITIONAL_DAYS_AWARDED,
      REMAND,
      TAGGED_BAIL,
    ) + 1 == numberOfDaysToDeterminateReleaseDate
  }

  private fun getAdjustmentTypes(): Array<AdjustmentType> {
    return if (sentence is AFineSentence && sentence.offence.isCivilOffence()) {
      emptyArray()
    } else if (sentence.isBotus()) {
      emptyArray()
    } else if (sentence.isDto() && sentence.isIdentificationTrackInitialized() && sentence.identificationTrack == SentenceIdentificationTrack.DTO_BEFORE_PCSC) {
      arrayOf(TAGGED_BAIL)
    } else if (!sentence.isRecall()) {
      arrayOf(REMAND, TAGGED_BAIL)
    } else {
      arrayOf(RECALL_REMAND, RECALL_TAGGED_BAIL)
    }
  }

  val calculatedDeterminateTotalDeductedDays: Int
    get() {
      return getDeterminateAdjustmentBeforeSentence(*getAdjustmentTypes())
    }

  val calculatedTotalDeductedDays: Int
    get() {
      return getDeterminateAdjustmentBeforeSentence(*getAdjustmentTypes())
    }
  val calculatedDeterminateTotalAddedDays: Int
    get() {
      return getDeterminateAdjustmentsAfterSentenceAtDate()
    }

  val calculatedTotalAddedDays: Int
    get() {
      return getAdjustmentsAfterSentenceAtDate()
    }

  val calculatedTotalAddedDaysForSled: Int
    get() {
      return if (isReleaseDateConditional && !sentence.isRecall()) {
        adjustments.applyPeriodsOfUALIncrementally(
          initialEndDate = unadjustedDeterminateReleaseDate,
          startDate = sentence.sentencedAt.minusDays(1),
        )
      } else {
        getAdjustmentsAfterSentenceAtDate()
      }
    }
  val calculatedTotalAddedDaysForTused: Int
    get() {
      return if (sentence.isRecall() && !sentence.recallType!!.isFixedTermRecall) {
        if (returnToCustodyDate != null) {
          adjustments.applyPeriodsOfUALIncrementally(
            startDate = sentence.sentencedAt,
            initialEndDate = returnToCustodyDate,
          )
        } else {
          getAdjustmentsAfterSentenceAtDate()
        }
      } else {
        adjustments.applyPeriodsOfUALIncrementally(
          startDate = sentence.sentencedAt.minusDays(1),
          initialEndDate = unadjustedExpiryDate,
        )
      }
    }

  fun getTotalAddedDaysAfter(after: LocalDate): Int {
    return adjustments.applyPeriodsOfUALIncrementally(
      startDate = after,
      initialEndDate = latestConcurrentRelease,
    )
  }

  private val calculatedFixedTermRecallAddedDays: Int
    get() {
      return adjustments.applyPeriodsOfUALIncrementally(
        startDate = returnToCustodyDate,
        initialEndDate = latestConcurrentRelease,
      )
    }

  val calculatedTotalAwardedDays: Int
    get() {
      if (sentence is AFineSentence) {
        return 0
      }
      return max(
        0,
        getAdjustmentDuringSentence(ADDITIONAL_DAYS_AWARDED) -
          getAdjustmentDuringSentence(RESTORATION_OF_ADDITIONAL_DAYS_AWARDED, ADDITIONAL_DAYS_SERVED),
      )
    }

  val calculatedUnusedLicenseAda: Int
    get() {
      return getAdjustmentDuringSentence(LICENSE_UNUSED_ADA)
    }

  val numberOfDaysToAddToLicenceExpiryDate: Int
    get() {
      if (!sentence.isRecall() && calculatedTotalDeductedDays >= numberOfDaysToDeterminateReleaseDate) {
        return calculatedTotalDeductedDays - numberOfDaysToDeterminateReleaseDate
      }
      return 0
    }

  val adjustedExpiryDate: LocalDate
    get() {
      return unadjustedExpiryDate
        .minusDays(
          calculatedTotalDeductedDays.toLong(),
        ).plusDays(
          calculatedTotalAddedDaysForSled.toLong(),
        )
    }

  val releaseDateWithoutAwarded: LocalDate
    get() {
      return unadjustedDeterminateReleaseDate.minusDays(
        calculatedDeterminateTotalDeductedDays.toLong(),
      ).plusDays(
        calculatedDeterminateTotalAddedDays.toLong(),
      )
    }

  /*
    Determinate release date that is not "capped" by the sentence date or expiry date.
   */
  val adjustedUncappedDeterminateReleaseDate: LocalDate
    get() {
      return releaseDateWithoutAwarded.plusDays(
        calculatedTotalAwardedDays.toLong(),
      )
    }

  val adjustedDeterminateReleaseDate: LocalDate
    get() {
      val date = adjustedUncappedDeterminateReleaseDate.minusDays(unusedAdaDays)
      return if (date.isAfter(sentence.sentencedAt)) {
        date
      } else {
        sentence.sentencedAt
      }
    }

  val adjustedHistoricDeterminateReleaseDate: LocalDate
    get() {
      val date = unadjustedHistoricDeterminateReleaseDate.minusDays(
        calculatedDeterminateTotalDeductedDays.toLong(),
      ).plusDays(
        calculatedDeterminateTotalAddedDays.toLong(),
      ).plusDays(
        calculatedTotalAwardedDays.toLong(),
      ).minusDays(unusedAdaDays)
      return if (date.isAfter(sentence.sentencedAt)) {
        date
      } else {
        sentence.sentencedAt
      }
    }
  val adjustedPostRecallReleaseDate: LocalDate?
    get() {
      if (sentence.isRecall()) {
        if (sentence.recallType == RecallType.STANDARD_RECALL) {
          return unadjustedPostRecallReleaseDate?.minusDays(
            calculatedTotalDeductedDays.toLong(),
          )?.plusDays(
            calculatedTotalAddedDays.toLong(),
          )
        }
        if (sentence.recallType!!.isFixedTermRecall) {
          // Fixed term recalls only apply adjustments from return to custody date
          val fixedTermRecallRelease = unadjustedPostRecallReleaseDate?.plusDays(
            calculatedFixedTermRecallAddedDays.toLong(),
          )
          return minOf(fixedTermRecallRelease!!, expiryDate)
        }
      }
      return null
    }

  // Non Parole Date (NPD)
  var numberOfDaysToNonParoleDate: Long = 0
  var nonParoleDate: LocalDate? = null

  val unadjustedExtendedDeterminateParoleEligibilityDate: LocalDate?
    get() {
      if (numberOfDaysToParoleEligibilityDate == null) {
        return null
      }
      return sentence.sentencedAt
        .plusDays(numberOfDaysToParoleEligibilityDate)
        .minusDays(1)
    }

  // Parole Eligibility Date (PED). This is only used for EDS, for SDS the PED is the release date.
  val extendedDeterminateParoleEligibilityDate: LocalDate?
    get() {
      if (unadjustedExtendedDeterminateParoleEligibilityDate == null) {
        return null
      }
      return unadjustedExtendedDeterminateParoleEligibilityDate!!
        .plusDays(calculatedTotalAddedDays.toLong())
        .minusDays(calculatedTotalDeductedDays.toLong())
        .plusDays(calculatedTotalAwardedDays.toLong())
    }

  val earlyReleaseSchemeEligibilityDate: LocalDate?
    get() {
      return breakdownByReleaseDateType[ReleaseDateType.ERSED]?.releaseDate
    }

  // Licence Expiry Date (LED)
  var numberOfDaysToLicenceExpiryDate: Long = 0
  private var _licenceExpiryDate: LocalDate? = null
  var licenceExpiryDate: LocalDate?
    get() {
      return if (sentence.releaseDateTypes.getReleaseDateTypes().contains(ReleaseDateType.SLED)) {
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

  var earlyTransferDate: LocalDate? = null
  var latestTransferDate: LocalDate? = null

  val releaseDate: LocalDate
    get() {
      return if (sentence.isRecall()) {
        adjustedPostRecallReleaseDate!!
      } else {
        adjustedDeterminateReleaseDate
      }
    }

  val releaseDateWithoutDeductions: LocalDate
    get() {
      return unadjustedDeterminateReleaseDate.plusDays(
        calculatedDeterminateTotalAddedDays.toLong(),
      ).plusDays(calculatedTotalAwardedDays.toLong())
    }

  val releaseDateWithoutAdditions: LocalDate
    get() {
      return if (sentence.recallType == RecallType.STANDARD_RECALL) {
        releaseDate.minusDays(calculatedTotalAddedDays.toLong())
      } else if (sentence.recallType?.isFixedTermRecall == true) {
        releaseDate.minusDays(calculatedFixedTermRecallAddedDays.toLong())
      } else {
        releaseDate.minusDays(calculatedTotalAddedDays.toLong()).minusDays(calculatedTotalAwardedDays.toLong())
      }
    }

  val expiryDate: LocalDate
    get() {
      return maxOf(adjustedExpiryDate, sentence.sentencedAt)
    }
  var topUpSupervisionDate: LocalDate? = null
  var isReleaseDateConditional: Boolean = false

  fun buildString(releaseDateTypes: List<ReleaseDateType>): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val expiryDateType = if (releaseDateTypes.contains(ReleaseDateType.SLED)) "SLED" else "SED"
    val releaseDateType = if (releaseDateTypes.contains(ReleaseDateType.ARD)) {
      "ARD"
    } else if (releaseDateTypes.contains(ReleaseDateType.CRD)) {
      "CRD"
    } else {
      "PED"
    }

    return "Sentence type: ${sentence.javaClass.name}\n" +
      "Date of $expiryDateType\t:\t${unadjustedExpiryDate.format(formatter)}\n" +
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
      "Effective $expiryDateType\t:\t${expiryDate.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${releaseDate.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${topUpSupervisionDate?.format(formatter)}\n"
  }
}
