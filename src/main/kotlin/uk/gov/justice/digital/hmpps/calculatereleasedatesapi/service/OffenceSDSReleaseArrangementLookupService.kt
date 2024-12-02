package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionSchedulePart
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period

@Service
class OffenceSDSReleaseArrangementLookupService(
  private val manageOffencesService: ManageOffencesService,
  private val featureToggles: FeatureToggles,
) {

  fun populateReleaseArrangements(sentencesAndOffences: List<SentenceAndOffence>): List<SentenceAndOffenceWithReleaseArrangements> {
    log.info("Checking ${sentencesAndOffences.size} sentences for SDS release arrangements")
    val checkedForSDSPlus = checkForSDSPlus(sentencesAndOffences)

    val offencesToCheckForSDSExclusions = getOffencesToCheckForSDSExclusions(checkedForSDSPlus)
    val exclusionsForOffences = if (offencesToCheckForSDSExclusions.isNotEmpty() && featureToggles.sdsEarlyRelease) manageOffencesService.getSdsExclusionsForOffenceCodes(offencesToCheckForSDSExclusions).associateBy { it.offenceCode } else emptyMap()

    return checkedForSDSPlus
      .map { sdsPlusCheckResult ->
        if (sdsPlusCheckResult.shouldCheckSDSEarlyReleaseExclusions() && sdsPlusCheckResult.sentenceAndOffence.offence.offenceCode in exclusionsForOffences) {
          SentenceAndOffenceWithReleaseArrangements(sdsPlusCheckResult.sentenceAndOffence, sdsPlusCheckResult.isSDSPlus, exclusionForOffence(exclusionsForOffences, sdsPlusCheckResult.sentenceAndOffence))
        } else {
          SentenceAndOffenceWithReleaseArrangements(sdsPlusCheckResult.sentenceAndOffence, sdsPlusCheckResult.isSDSPlus, SDSEarlyReleaseExclusionType.NO)
        }
      }
  }

  private fun checkForSDSPlus(sentencesAndOffences: List<SentenceAndOffence>): List<SDSPlusCheckResult> {
    val offencesToCheckForSDSPlus = getOffencesToCheckForSDSPlus(sentencesAndOffences)
    val sdsPlusMarkersByOffences = if (offencesToCheckForSDSPlus.isNotEmpty()) manageOffencesService.getPcscMarkersForOffenceCodes(*offencesToCheckForSDSPlus.toTypedArray()).associateBy { it.offenceCode } else emptyMap()
    return sentencesAndOffences.map { sentencesAndOffence ->
      if (sentencesAndOffence.offence.offenceCode in sdsPlusMarkersByOffences && checkIsSDSPlus(sentencesAndOffence, sdsPlusMarkersByOffences)) {
        SDSPlusCheckResult(sentencesAndOffence, true)
      } else if (isForOffenceThatIsNowOnListDButUsedOldOffenceCode(sentencesAndOffence)) {
        SDSPlusCheckResult(sentencesAndOffence, true)
      } else {
        SDSPlusCheckResult(sentencesAndOffence, false)
      }
    }
  }

  private fun isForOffenceThatIsNowOnListDButUsedOldOffenceCode(sentenceAndOffence: SentenceAndOffence): Boolean =
    SentenceCalculationType.isSupported(sentenceAndOffence.sentenceCalculationType) &&
      SentenceCalculationType.isSDSPlusEligible(sentenceAndOffence.sentenceCalculationType, SentenceCalculationType.SDSPlusEligibilityType.SDS) &&
      sentenceAndOffence.offence.offenceCode in LEGACY_OFFENCE_CODES_FOR_OFFENCES_ON_LIST_D &&
      sevenYearsOrMore(sentenceAndOffence) &&
      sentencedAfterPcsc(sentenceAndOffence)

  private fun checkIsSDSPlus(
    sentenceAndOffence: SentenceAndOffence,
    sdsPlusMarkersByOffences: Map<String, OffencePcscMarkers>,
  ): Boolean {
    val moResponseForOffence = sdsPlusMarkersByOffences[sentenceAndOffence.offence.offenceCode]
    val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffence)
    val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffence)
    var sdsPlusIdentified = false
    if (SentenceCalculationType.isSDSPlusEligible(sentenceAndOffence.sentenceCalculationType, SentenceCalculationType.SDSPlusEligibilityType.SDS)) {
      if (sentencedWithinOriginalSdsPlusWindow(sentenceAndOffence) && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListA == true) {
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListD == true) {
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && fourToUnderSeven(sentenceAndOffence) && moResponseForOffence?.pcscMarkers?.inListB == true) {
        sdsPlusIdentified = true
      }
    } else if (SentenceCalculationType.isSDSPlusEligible(sentenceAndOffence.sentenceCalculationType, SentenceCalculationType.SDSPlusEligibilityType.SECTION250)) {
      if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListC == true) {
        sdsPlusIdentified = true
      }
    }
    return sdsPlusIdentified
  }

  private fun getOffencesToCheckForSDSPlus(sentencesAndOffences: List<SentenceAndOffence>): List<String> {
    return sentencesAndOffences
      .filter { SentenceCalculationType.isSupported(it.sentenceCalculationType) }
      .filter { sentenceAndOffences ->
        val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffences)
        val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffences)
        val sentencedWithinOriginalSdsWindow = sentencedWithinOriginalSdsPlusWindow(sentenceAndOffences)

        var matchFilter = false
        if (SentenceCalculationType.isSDSPlusEligible(sentenceAndOffences.sentenceCalculationType, SentenceCalculationType.SDSPlusEligibilityType.SDS)) {
          if (sentencedWithinOriginalSdsWindow && sevenYearsOrMore) {
            matchFilter = true
          } else if (sentenceIsAfterPcsc && fourYearsOrMore(sentenceAndOffences)) {
            matchFilter = true
          }
        } else if (SentenceCalculationType.isSDSPlusEligible(sentenceAndOffences.sentenceCalculationType, SentenceCalculationType.SDSPlusEligibilityType.SECTION250) && sentenceIsAfterPcsc && sevenYearsOrMore) {
          matchFilter = true
        }
        matchFilter
      }.map { it.offence.offenceCode }
  }

  private fun sentencedAfterPcsc(sentence: SentenceAndOffence): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(
      ImportantDates.PCSC_COMMENCEMENT_DATE,
    )
  }

  private fun sentencedWithinOriginalSdsPlusWindow(sentence: SentenceAndOffence): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) && !sentencedAfterPcsc(sentence)
  }

  private fun endOfSentence(sentence: SentenceAndOffence): LocalDate {
    val duration =
      Period.of(sentence.terms[0].years, sentence.terms[0].months, sentence.terms[0].weeks * 7 + sentence.terms[0].days)
    return sentence.sentenceDate.plus(duration)
  }

  private fun sevenYearsOrMore(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfSevenYears)
  }

  private fun fourToUnderSeven(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears) && endOfSentence.isBefore(endOfSevenYears)
  }

  private fun fourYearsOrMore(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears)
  }

  private fun getOffencesToCheckForSDSExclusions(checkedForSDSPlus: List<SDSPlusCheckResult>): List<String> = checkedForSDSPlus
    .filter { it.shouldCheckSDSEarlyReleaseExclusions() }
    .map { it.sentenceAndOffence.offence.offenceCode }

  private fun SDSPlusCheckResult.shouldCheckSDSEarlyReleaseExclusions(): Boolean =
    SentenceCalculationType.isSDS40Eligible(sentenceAndOffence.sentenceCalculationType) &&
      !isSDSPlus

  private fun exclusionForOffence(exclusionsForOffences: Map<String, SDSEarlyReleaseExclusionForOffenceCode>, sentenceAndOffence: SentenceAndOffence): SDSEarlyReleaseExclusionType {
    val offenceCode = sentenceAndOffence.offence.offenceCode
    val exclusionForOffence = exclusionsForOffences[offenceCode]

    return when (exclusionForOffence?.schedulePart) {
      SDSEarlyReleaseExclusionSchedulePart.SEXUAL_T3 -> SDSEarlyReleaseExclusionType.SEXUAL_T3
      SDSEarlyReleaseExclusionSchedulePart.SEXUAL -> SDSEarlyReleaseExclusionType.SEXUAL
      SDSEarlyReleaseExclusionSchedulePart.DOMESTIC_ABUSE_T3 -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE_T3
      SDSEarlyReleaseExclusionSchedulePart.DOMESTIC_ABUSE -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE
      SDSEarlyReleaseExclusionSchedulePart.NATIONAL_SECURITY_T3 -> SDSEarlyReleaseExclusionType.NATIONAL_SECURITY_T3
      SDSEarlyReleaseExclusionSchedulePart.NATIONAL_SECURITY -> SDSEarlyReleaseExclusionType.NATIONAL_SECURITY
      SDSEarlyReleaseExclusionSchedulePart.TERRORISM_T3 -> SDSEarlyReleaseExclusionType.TERRORISM_T3
      SDSEarlyReleaseExclusionSchedulePart.TERRORISM -> SDSEarlyReleaseExclusionType.TERRORISM
      SDSEarlyReleaseExclusionSchedulePart.MURDER_T3 -> SDSEarlyReleaseExclusionType.MURDER_T3
      SDSEarlyReleaseExclusionSchedulePart.MURDER -> SDSEarlyReleaseExclusionType.MURDER
      SDSEarlyReleaseExclusionSchedulePart.VIOLENT_T3 ->
        if (fourYearsOrMore(sentenceAndOffence)) SDSEarlyReleaseExclusionType.VIOLENT_T3 else SDSEarlyReleaseExclusionType.NO
      SDSEarlyReleaseExclusionSchedulePart.VIOLENT ->
        if (fourYearsOrMore(sentenceAndOffence)) SDSEarlyReleaseExclusionType.VIOLENT else SDSEarlyReleaseExclusionType.NO
      SDSEarlyReleaseExclusionSchedulePart.NONE -> SDSEarlyReleaseExclusionType.NO
      null -> SDSEarlyReleaseExclusionType.NO
    }
  }

  private data class SDSPlusCheckResult(val sentenceAndOffence: SentenceAndOffence, val isSDSPlus: Boolean)
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

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
