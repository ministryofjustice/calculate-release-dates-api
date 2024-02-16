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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
      adjustmentsAfter = adjustmentsAfter,
    )
  }

  fun getDeterminateAdjustmentBeforeSentence(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentDeterminateRelease,
      adjustmentsAfter = adjustmentsAfter,
    )
  }

  fun getDeterminateAdjustmentsAfterSentenceAtDate(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentDeterminateRelease,
      adjustmentsAfter = if (adjustmentsAfter != null) adjustmentsAfter else sentence.sentencedAt.minusDays(1),
    )
  }

  fun getAdjustmentsAfterSentenceAtDate(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = latestConcurrentRelease,
      adjustmentsAfter = if (adjustmentsAfter != null) adjustmentsAfter else sentence.sentencedAt.minusDays(1),
    )
  }

  fun getAdjustmentDuringSentence(vararg adjustmentTypes: AdjustmentType): Int {
    return adjustments.getOrZero(
      *adjustmentTypes,
      adjustmentsBefore = releaseDateWithoutAwarded,
      adjustmentsAfter = adjustmentsAfter,
    )
  }

  fun isImmediateRelease(): Boolean {
    return sentence.sentencedAt == adjustedDeterminateReleaseDate
  }

  private fun getAdjustmentTypes(): Array<AdjustmentType> {
    return if (sentence is AFineSentence && sentence.offence.isCivilOffence()) {
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
      return getAdjustmentBeforeSentence(*getAdjustmentTypes())
    }
  val calculatedDeterminateTotalAddedDays: Int
    get() {
      return getDeterminateAdjustmentsAfterSentenceAtDate(UNLAWFULLY_AT_LARGE)
    }

  val calculatedTotalAddedDays: Int
    get() {
      return getAdjustmentsAfterSentenceAtDate(UNLAWFULLY_AT_LARGE)
    }

  val calculatedTotalAddedDaysForSled: Int
    get() {
      if (isReleaseDateConditional && !sentence.isRecall()) {
        return adjustments.getOrZero(
          UNLAWFULLY_AT_LARGE,
          adjustmentsBefore = unadjustedDeterminateReleaseDate,
          adjustmentsAfter = sentence.sentencedAt.minusDays(1),
        )
      } else {
        return getAdjustmentsAfterSentenceAtDate(UNLAWFULLY_AT_LARGE)
      }
    }
  val calculatedTotalAddedDaysForTused: Int
    get() {
      return if (sentence.isRecall() && !sentence.recallType!!.isFixedTermRecall) {
        if (returnToCustodyDate != null) {
          adjustments.getOrZero(
            UNLAWFULLY_AT_LARGE,
            adjustmentsBefore = returnToCustodyDate,
            adjustmentsAfter = sentence.sentencedAt,
          )
        } else {
          getAdjustmentsAfterSentenceAtDate(UNLAWFULLY_AT_LARGE)
        }
      } else {
        adjustments.getOrZero(
          UNLAWFULLY_AT_LARGE,
          adjustmentsBefore = releaseDate,
          adjustmentsAfter = sentence.sentencedAt.minusDays(1),
        )
      }
    }

  fun getTotalAddedDaysAfter(after: LocalDate): Int {
    return adjustments.getOrZero(
      UNLAWFULLY_AT_LARGE,
      adjustmentsBefore = latestConcurrentRelease,
      adjustmentsAfter = after,
    )
  }

  val calculatedFixedTermRecallAddedDays: Int
    get() {
      return adjustments.getOrZero(
        UNLAWFULLY_AT_LARGE,
        adjustmentsBefore = latestConcurrentRelease,
        adjustmentsAfter = returnToCustodyDate,
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

  val calculatedUnusedReleaseAda: Int
    get() {
      return getAdjustmentDuringSentence(RELEASE_UNUSED_ADA)
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
      val date = adjustedUncappedDeterminateReleaseDate.minusDays(calculatedUnusedReleaseAda.toLong())
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
  var numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate: Long = 0
  var homeDetentionCurfewEligibilityDate: LocalDate? = null
  var homeDetentionCurfew4PlusEligibilityDate: LocalDate? = null

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

  fun buildString(releaseDateTypes: ReleaseDateTypes): String {
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
      "Number of days to Home Detention Curfew Eligibility Date 4+(HDCED4PLUS)\t:\t" +
      "${numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate}\n" +
      "Home Detention Curfew Eligibility Date 4+(HDCED4PLUS)\t:\t" +
      "${homeDetentionCurfew4PlusEligibilityDate?.format(formatter)}\n" +
      "Effective $expiryDateType\t:\t${expiryDate.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${releaseDate.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${topUpSupervisionDate?.format(formatter)}\n"
  }
}
