package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SentenceCalculation(
  val unadjustedReleaseDate: UnadjustedReleaseDate,
  var adjustments: SentenceAdjustments,
  val calculateErsed: Boolean,
  var trancheCommencement: LocalDate? = null,
  val sentence: CalculableSentence = unadjustedReleaseDate.sentence,
) {
  val releaseDateCalculation: ReleaseDateCalculation get() = unadjustedReleaseDate.releaseDateCalculation
  val numberOfDaysToSentenceExpiryDate: Int get() = releaseDateCalculation.numberOfDaysToSentenceExpiryDate
  val numberOfDaysToDeterminateReleaseDateDouble: Double get() = releaseDateCalculation.numberOfDaysToDeterminateReleaseDateDouble
  val numberOfDaysToDeterminateReleaseDate: Int get() = releaseDateCalculation.numberOfDaysToDeterminateReleaseDate
  val unadjustedExpiryDate get() = unadjustedReleaseDate.unadjustedExpiryDate
  val unadjustedDeterminateReleaseDate get() = unadjustedReleaseDate.unadjustedDeterminateReleaseDate
  val unadjustedPostRecallReleaseDate: LocalDate? get() = unadjustedReleaseDate.unadjustedPostRecallReleaseDate

  val adjustedHistoricDeterminateReleaseDate: LocalDate
    get() {
      val date = unadjustedReleaseDate.unadjustedHistoricDeterminateReleaseDate
        .plusDays(adjustments.adjustmentsForInitalRelease())
        .minusDays(adjustments.unusedAdaDays)
      return if (date.isAfter(sentence.sentencedAt)) {
        date
      } else {
        sentence.sentencedAt
      }
    }

  val adjustedDeterminateReleaseDate: LocalDate
    get() {
      val date = adjustedUncappedDeterminateReleaseDate.minusDays(adjustments.unusedAdaDays)
      return if (date.isAfter(sentence.sentencedAt)) {
        date
      } else {
        sentence.sentencedAt
      }
    }

  fun isImmediateRelease(): Boolean = sentence.sentencedAt == adjustedDeterminateReleaseDate

  fun isImmediateCustodyRelease(): Boolean = isImmediateRelease() && (1 - adjustments.adjustmentsForInitalRelease()) == releaseDateCalculation.numberOfDaysToDeterminateReleaseDate.toLong()

  val numberOfDaysToAddToLicenceExpiryDate: Int
    get() {
      if (!sentence.isRecall() && adjustments.deductions >= releaseDateCalculation.numberOfDaysToDeterminateReleaseDate) {
        return (adjustments.deductions - releaseDateCalculation.numberOfDaysToDeterminateReleaseDate).toInt()
      }
      return 0
    }

  val adjustedExpiryDate: LocalDate
    get() {
      return unadjustedReleaseDate.unadjustedExpiryDate
        .plusDays(adjustments.adjustmentsForLicenseExpiry())
    }

  val releaseDateWithoutAwarded: LocalDate
    get() {
      return this.unadjustedReleaseDate.unadjustedDeterminateReleaseDate
        .plusDays(adjustments.adjustmentsForInitialReleaseWithoutAwarded())
    }

  val adjustedUncappedDeterminateReleaseDate: LocalDate
    get() {
      return releaseDateWithoutAwarded.plusDays(
        adjustments.awardedDuringCustody - adjustments.servedAdaDays,
      )
    }

  val adjustedPostRecallReleaseDate: LocalDate?
    get() {
      if (sentence.isRecall()) {
        if (sentence.recallType == RecallType.STANDARD_RECALL) {
          return unadjustedPostRecallReleaseDate?.plusDays(
            adjustments.adjustmentsForStandardRecall(),
          )
        }
        if (sentence.recallType!!.isFixedTermRecall) {
          // Fixed term recalls only apply adjustments from return to custody date
          val fixedTermRecallRelease = unadjustedPostRecallReleaseDate?.plusDays(
            adjustments.adjustmentsForFixedTermRecall(),
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
      if (releaseDateCalculation.numberOfDaysToParoleEligibilityDate == null) {
        return null
      }
      return sentence.sentencedAt
        .plusDays(releaseDateCalculation.numberOfDaysToParoleEligibilityDate!!)
        .minusDays(1)
    }

  // Parole Eligibility Date (PED). This is only used for EDS, for SDS the PED is the release date.
  val extendedDeterminateParoleEligibilityDate: LocalDate?
    get() {
      if (unadjustedExtendedDeterminateParoleEligibilityDate == null) {
        return null
      }
      return unadjustedExtendedDeterminateParoleEligibilityDate!!
        .plusDays(adjustments.adjustmentsForInitalRelease())
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

  //  Home Detention Curfew Eligibility Date 365 (HDC-365)
  // These will eventually replace numberOfDaysToHomeDetentionCurfewEligibilityDate and homeDetentionCurfewEligibilityDate
  // at the moment calculating both until the commencement date
  var numberOfDaysToHomeDetentionCurfewEligibilityDateHDC365: Long = 0
  var homeDetentionCurfewEligibilityDateHDC365: LocalDate? = null

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

  val releaseDateDefaultedByCommencement: LocalDate
    get() {
      return if (isDateDefaultedToCommencement(adjustedDeterminateReleaseDate)) {
        trancheCommencement!!
      } else {
        releaseDate
      }
    }

  val releaseDateWithoutDeductions: LocalDate
    get() {
      return adjustedUncappedDeterminateReleaseDate.plusDays(adjustments.deductions)
    }

  val expiryDate: LocalDate
    get() {
      return maxOf(adjustedExpiryDate, sentence.sentencedAt)
    }
  var topUpSupervisionDate: LocalDate? = null
  var isReleaseDateConditional: Boolean = false

  private fun isDateDefaultedToCommencement(releaseDate: LocalDate): Boolean = !sentence.isRecall() && trancheCommencement != null && sentence.sentenceParts().any { it.identificationTrack.isEarlyReleaseTrancheOneTwo() } && releaseDate.isBefore(trancheCommencement)

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
      "Number of days to $releaseDateType\t:\t${releaseDateCalculation.numberOfDaysToDeterminateReleaseDate}\n" +
      "Date of $releaseDateType\t:\t${unadjustedDeterminateReleaseDate.format(formatter)}\n" +
      "Adjustments ${adjustments}\n" +

      "Total number of days to Licence Expiry Date (LED)\t:\t${numberOfDaysToLicenceExpiryDate}\n" +
      "LED\t:\t${licenceExpiryDate?.format(formatter)}\n" +

      "Number of days to Non Parole Date (NPD)\t:\t${numberOfDaysToNonParoleDate}\n" +
      "Non Parole Date (NPD)\t:\t${nonParoleDate?.format(formatter)}\n" +

      "Number of days to Home Detention Curfew Eligibility Date (HDCED)\t:\t" +
      "${numberOfDaysToHomeDetentionCurfewEligibilityDate}\n" +
      "Home Detention Curfew Eligibility Date (HDCED)\t:\t" +
      "${homeDetentionCurfewEligibilityDate?.format(formatter)}\n" +
      "Number of days to Home Detention Curfew Eligibility Date (HDC-365)\t:\t" +
      "${numberOfDaysToHomeDetentionCurfewEligibilityDateHDC365}\n" +
      "Home Detention Curfew Eligibility Date using HDC-365 rules\t:\t" +
      "${homeDetentionCurfewEligibilityDateHDC365?.format(formatter)}\n" +
      "Effective $expiryDateType\t:\t${expiryDate.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${releaseDate.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${topUpSupervisionDate?.format(formatter)}\n"
  }
}
