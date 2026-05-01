package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period

class SDSPlusCheckResult(
  val sentenceAndOffence: SentenceAndOffence,
  val pcscMarkers: PcscMarkers,
) {
  val isSDSPlus: Boolean
  val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean
  private val listDExtended: Boolean
  private val eligibilityType: SentenceCalculationType.SDSPlusEligibilityType

  init {

    val offenceCode = sentenceAndOffence.offence.offenceCode

    val eligibleSentence = SentenceCalculationType.isCalculable(sentenceAndOffence.sentenceCalculationType) &&
      SentenceCalculationType.isSDSPlusEligible(sentenceAndOffence.sentenceCalculationType)
    listDExtended = offenceCode in LEGACY_OFFENCE_CODES_FOR_OFFENCES_ON_LIST_D

    eligibilityType = getSentenceEligibilityType(sentenceAndOffence.sentenceCalculationType)
    val isSDSPlusOffenceInPeriod = offenceWithinDateRangeForLists()

    isSDSPlusEligibleSentenceTypeLengthAndOffence = eligibleSentence && eligibleSentenceTypeLengthAndOffence()

    isSDSPlus = isSDSPlusEligibleSentenceTypeLengthAndOffence && isSDSPlusOffenceInPeriod
  }

  private fun eligibleSentenceTypeLengthAndOffence(): Boolean = when (eligibilityType) {
    SentenceCalculationType.SDSPlusEligibilityType.SDS -> {
      when {
        (sevenYearsOrMore() && (pcscMarkers.inListA || pcscMarkers.inListD || listDExtended)) -> true
        (fourToUnderSeven() && pcscMarkers.inListB) -> true
        else -> false
      }
    }
    SentenceCalculationType.SDSPlusEligibilityType.SECTION250 -> {
      sevenYearsOrMore() && pcscMarkers.inListC
    }
    else -> false
  }

  private fun offenceWithinDateRangeForLists(): Boolean = when (eligibilityType) {
    SentenceCalculationType.SDSPlusEligibilityType.SDS -> evaluateSdsEligibility()
    SentenceCalculationType.SDSPlusEligibilityType.SECTION250 -> sentencedAfterPcsc()
    SentenceCalculationType.SDSPlusEligibilityType.NONE -> false
  }

  private fun evaluateSdsEligibility(): Boolean = when {
    fourToUnderSeven() -> sentencedAfterPcsc()
    sevenYearsOrMore() -> evaluateEligibilityForSevenYearsOrMore()
    else -> false
  }

  private fun evaluateEligibilityForSevenYearsOrMore(): Boolean = if (listDExtended) {
    sentencedAfterPcsc()
  } else {
    sentencedAfterPcsc() || pcscMarkers.inListA && sentencedWithinOriginalSdsPlusWindow()
  }

  private fun getSentenceEligibilityType(sentenceCalculationType: String): SentenceCalculationType.SDSPlusEligibilityType {
    if (SentenceCalculationType.isSDSPlusEligible(sentenceCalculationType, SentenceCalculationType.SDSPlusEligibilityType.SDS)) {
      return SentenceCalculationType.SDSPlusEligibilityType.SDS
    }

    if (SentenceCalculationType.isSDSPlusEligible(sentenceCalculationType, SentenceCalculationType.SDSPlusEligibilityType.SECTION250)) {
      return SentenceCalculationType.SDSPlusEligibilityType.SECTION250
    }

    return SentenceCalculationType.SDSPlusEligibilityType.NONE
  }

  private fun sentencedWithinOriginalSdsPlusWindow(): Boolean = sentenceAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) &&
    !sentencedAfterPcsc()

  private fun sentencedAfterPcsc(): Boolean = sentenceAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.PCSC_COMMENCEMENT_DATE)

  private fun endOfSentence(): LocalDate {
    val term = sentenceAndOffence.terms.firstOrNull()
    if (term == null) {
      return sentenceAndOffence.sentenceDate
    }
    val duration = Period.of(term.years, term.months, term.weeks * 7 + term.days)
    return sentenceAndOffence.sentenceDate.plus(duration)
  }

  private fun sevenYearsOrMore(): Boolean {
    val endOfSentence = endOfSentence()
    val endOfSevenYears = sentenceAndOffence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfSevenYears)
  }

  private fun fourToUnderSeven(): Boolean {
    val endOfSentence = endOfSentence()
    val endOfFourYears = sentenceAndOffence.sentenceDate.plusYears(4)
    val endOfSevenYears = sentenceAndOffence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears) && endOfSentence.isBefore(endOfSevenYears)
  }

  companion object {
    private val LEGACY_OFFENCE_CODES_FOR_OFFENCES_ON_LIST_D = listOf(
      "DV04001",
      "RT88001",
      "RT88500",
      "RT88527",
      "RT88338",
      "RT88583",
      "RA88043",
      "RT88337",
      "RT88554",
      "RT88029",
      "RT88502",
      "RT88028",
      "RT88027",
      "RT88579",
    ).flatMap { offenceCodeWithoutSuffix ->
      listOf(
        offenceCodeWithoutSuffix,
        "${offenceCodeWithoutSuffix}A",
        "${offenceCodeWithoutSuffix}B",
        "${offenceCodeWithoutSuffix}C",
        "${offenceCodeWithoutSuffix}I",
      )
    }
  }
}
