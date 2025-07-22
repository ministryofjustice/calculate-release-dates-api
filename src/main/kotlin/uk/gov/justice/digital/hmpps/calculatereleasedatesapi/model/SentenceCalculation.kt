package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model


import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.InterimHdcCalcType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class SentenceCalculation(
  val unadjustedReleaseDate: UnadjustedReleaseDate,
  var adjustments: SentenceAdjustments,
  val calculateErsed: Boolean,
  var allocatedEarlyRelease: EarlyReleaseConfiguration? = null,
  var allocatedTranche: EarlyReleaseTrancheConfiguration? = null,
  val sentence: CalculableSentence = unadjustedReleaseDate.sentence,
  var lastDayOfUal: LocalDate? = null,
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
        .plusDays(adjustments.adjustmentsForInitialRelease())
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

  fun getDateByType(type: ReleaseDateType): LocalDate? {
    if (!breakdownByReleaseDateType.containsKey(type)) {
      return null
    }

    return when (type) {
      ReleaseDateType.ARD -> adjustedDeterminateReleaseDate
      ReleaseDateType.CRD -> adjustedDeterminateReleaseDate
      ReleaseDateType.PRRD -> adjustedPostRecallReleaseDate
      ReleaseDateType.LED -> licenceExpiryDate
      ReleaseDateType.SED -> expiryDate
      ReleaseDateType.NPD -> nonParoleDate
      ReleaseDateType.TUSED -> topUpSupervisionDate
      ReleaseDateType.PED -> extendedDeterminateParoleEligibilityDate
      ReleaseDateType.SLED -> expiryDate
      ReleaseDateType.HDCED -> homeDetentionCurfewEligibilityDate
      ReleaseDateType.NCRD -> notionalConditionalReleaseDate
      else -> null
    }
  }

  fun isImmediateRelease(): Boolean = sentence.sentencedAt == adjustedDeterminateReleaseDate

  fun isImmediateCustodyRelease(): Boolean = isImmediateRelease() && (1 - adjustments.adjustmentsForInitialRelease()) == releaseDateCalculation.numberOfDaysToDeterminateReleaseDate.toLong()

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
        .plusDays(adjustments.adjustmentsForLicenceExpiry())
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
          return expiryDate
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
        .plusDays(adjustments.adjustmentsForInitialRelease())
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

/*
 * This function will return the license expiry date at the point of initial release. If there was an immediate release and then a recall, more remand/tagged bail will be usable and therefore the license expiry will change, this returns the expiry before that change.
*/
  val licenceExpiryAtInitialRelease: LocalDate?
    get() {
      val adjustmentDaysForInitialRelease = adjustments.adjustmentsForInitialRelease()
      return if (adjustmentDaysForInitialRelease < 0 && abs(adjustmentDaysForInitialRelease) > numberOfDaysToDeterminateReleaseDate) {
        val unusedDays = abs(adjustmentDaysForInitialRelease) - numberOfDaysToDeterminateReleaseDate
        licenceExpiryDate?.plusDays(unusedDays)
      } else {
        licenceExpiryDate
      }
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

  val releaseDateDefaultedByCommencement: LocalDate
    get() {
      return if (isDateDefaultedToCommencement(adjustedDeterminateReleaseDate)) {
        allocatedTranche!!.date
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
      return listOfNotNull(adjustedExpiryDate, sentence.sentencedAt, lastDayOfUal?.plusDays(1)).max()
    }
  var topUpSupervisionDate: LocalDate? = null
  var isReleaseDateConditional: Boolean = false

  private fun isDateDefaultedToCommencement(releaseDate: LocalDate): Boolean = !sentence.isRecall() && allocatedTranche != null && allocatedEarlyRelease != null && sentence.sentenceParts().any { allocatedEarlyRelease!!.matchesFilter(it) } && releaseDate.isBefore(allocatedTranche!!.date)

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
      "Effective $expiryDateType\t:\t${expiryDate.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${releaseDate.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${topUpSupervisionDate?.format(formatter)}\n"
  }
}
