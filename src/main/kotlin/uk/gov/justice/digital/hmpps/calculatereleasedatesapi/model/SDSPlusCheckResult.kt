package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period

class SDSPlusCheckResult(
  val sentenceAndOffence: SentenceAndOffence,
  sdsPlusMarkersByOffences: Map<String, OffencePcscMarkers>,
) {
  val isSDSPlus: Boolean
  val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean
  val isSDSPlusOffenceInPeriod: Boolean
  private val eligibleSentence: Boolean
  private val offenceMarkers: OffencePcscMarkers?
  private val listDExtended: Boolean
  private val eligibilityType: SentenceCalculationType.SDSPlusEligibilityType

  init {

    val offenceCode = sentenceAndOffence.offence.offenceCode

    eligibleSentence = SentenceCalculationType.isSupported(sentenceAndOffence.sentenceCalculationType) &&
      SentenceCalculationType.isSDSPlusEligible(sentenceAndOffence.sentenceCalculationType)
    offenceMarkers = sdsPlusMarkersByOffences[offenceCode]
    listDExtended = offenceCode in LEGACY_OFFENCE_CODES_FOR_OFFENCES_ON_LIST_D

    eligibilityType = getSentenceEligibilityType(sentenceAndOffence.sentenceCalculationType)
    isSDSPlusOffenceInPeriod = offenceWithinDateRangeForLists()

    isSDSPlusEligibleSentenceTypeLengthAndOffence = eligibleSentence && eligibleSentenceTypeLengthAndOffence()

    isSDSPlus = isSDSPlusEligibleSentenceTypeLengthAndOffence && isSDSPlusOffenceInPeriod
  }

  private fun eligibleSentenceTypeLengthAndOffence(): Boolean {
    return when (eligibilityType) {
      SentenceCalculationType.SDSPlusEligibilityType.SDS -> {
        when {
          (sevenYearsOrMore() && (offenceMarkers?.pcscMarkers?.inListA == true || offenceMarkers?.pcscMarkers?.inListD == true || listDExtended)) -> true
          (fourToUnderSeven() && offenceMarkers?.pcscMarkers?.inListB == true) -> true
          else -> false
        }
      }
      SentenceCalculationType.SDSPlusEligibilityType.SECTION250 -> {
        sevenYearsOrMore() && offenceMarkers?.pcscMarkers?.inListC == true
      }
      else -> false
    }
  }

  private fun offenceWithinDateRangeForLists(): Boolean {
    return when (eligibilityType) {
      SentenceCalculationType.SDSPlusEligibilityType.SDS -> evaluateSdsEligibility()
      SentenceCalculationType.SDSPlusEligibilityType.SECTION250 -> sentencedAfterPcsc()
      SentenceCalculationType.SDSPlusEligibilityType.NONE -> false
    }
  }

  private fun evaluateSdsEligibility(): Boolean {
    return when {
      fourToUnderSeven() -> sentencedAfterPcsc()
      sevenYearsOrMore() -> evaluateEligibilityForSevenYearsOrMore()
      else -> false
    }
  }

  private fun evaluateEligibilityForSevenYearsOrMore(): Boolean {
    return if (listDExtended) {
      sentencedAfterPcsc()
    } else {
      sentencedAfterPcsc() || offenceMarkers?.pcscMarkers?.inListA == true && sentencedWithinOriginalSdsPlusWindow()
    }
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

  private fun sentencedWithinOriginalSdsPlusWindow(): Boolean {
    return sentenceAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) &&
      !sentencedAfterPcsc()
  }

  private fun sentencedAfterPcsc(): Boolean {
    return sentenceAndOffence.sentenceDate.isAfterOrEqualTo(ImportantDates.PCSC_COMMENCEMENT_DATE)
  }

  private fun endOfSentence(): LocalDate {
    val term = sentenceAndOffence.terms[0]
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
