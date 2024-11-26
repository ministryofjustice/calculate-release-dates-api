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
import java.util.EnumSet

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
      SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType) in SDS_AND_DYOI_POST_PCSC_CALC_TYPES &&
      sentenceAndOffence.offence.offenceCode in LEGACY_OFFENCE_CODES_FOR_OFFENCES_ON_LIST_D &&
      sevenYearsOrMore(sentenceAndOffence) &&
      sentencedAfterPcsc(sentenceAndOffence)

  private fun checkIsSDSPlus(
    sentenceAndOffence: SentenceAndOffence,
    sdsPlusMarkersByOffences: Map<String, OffencePcscMarkers>,
  ): Boolean {
    val moResponseForOffence = sdsPlusMarkersByOffences[sentenceAndOffence.offence.offenceCode]
    val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffence)
    val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)
    val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffence)
    var sdsPlusIdentified = false
    if (sentenceCalculationType in SDS_AND_DYOI_POST_PCSC_CALC_TYPES) {
      if (sentencedWithinOriginalSdsPlusWindow(sentenceAndOffence) && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListA == true) {
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListD == true) {
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && fourToUnderSeven(sentenceAndOffence) && moResponseForOffence?.pcscMarkers?.inListB == true) {
        sdsPlusIdentified = true
      }
    } else if (sentenceCalculationType in S250_POST_PCSC_CALC_TYPES) {
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
        val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffences.sentenceCalculationType)
        val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffences)
        val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffences)
        val sentencedWithinOriginalSdsWindow = sentencedWithinOriginalSdsPlusWindow(sentenceAndOffences)

        var matchFilter = false

        if (sentenceCalculationType in SDS_AND_DYOI_POST_PCSC_CALC_TYPES) {
          if (sentencedWithinOriginalSdsWindow && sevenYearsOrMore) {
            matchFilter = true
          } else if (sentenceIsAfterPcsc && fourYearsOrMore(sentenceAndOffences)) {
            matchFilter = true
          }
        } else if (sentenceCalculationType in S250_POST_PCSC_CALC_TYPES && sentenceIsAfterPcsc && sevenYearsOrMore) {
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
    SentenceCalculationType.isSupported(sentenceAndOffence.sentenceCalculationType) &&
      SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType) in SDS_EXCLUSION_CALC_TYPES &&
      !isSDSPlus

  private fun exclusionForOffence(exclusionsForOffences: Map<String, SDSEarlyReleaseExclusionForOffenceCode>, sentenceAndOffence: SentenceAndOffence): SDSEarlyReleaseExclusionType {
    val offenceCode = sentenceAndOffence.offence.offenceCode
    val exclusionForOffence = exclusionsForOffences[offenceCode]

    return when (exclusionForOffence?.schedulePart) {
      SDSEarlyReleaseExclusionSchedulePart.SEXUAL,
      SDSEarlyReleaseExclusionSchedulePart.SEXUAL_T3,
      -> SDSEarlyReleaseExclusionType.SEXUAL
      SDSEarlyReleaseExclusionSchedulePart.DOMESTIC_ABUSE,
      SDSEarlyReleaseExclusionSchedulePart.DOMESTIC_ABUSE_T3,
      -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE
      SDSEarlyReleaseExclusionSchedulePart.NATIONAL_SECURITY,
      SDSEarlyReleaseExclusionSchedulePart.NATIONAL_SECURITY_T3,
      -> SDSEarlyReleaseExclusionType.NATIONAL_SECURITY
      SDSEarlyReleaseExclusionSchedulePart.TERRORISM,
      SDSEarlyReleaseExclusionSchedulePart.TERRORISM_T3,
      -> SDSEarlyReleaseExclusionType.TERRORISM
      SDSEarlyReleaseExclusionSchedulePart.MURDER,
      SDSEarlyReleaseExclusionSchedulePart.MURDER_T3,
      -> SDSEarlyReleaseExclusionType.MURDER
      SDSEarlyReleaseExclusionSchedulePart.VIOLENT_T3,
      SDSEarlyReleaseExclusionSchedulePart.VIOLENT,
      -> if (fourYearsOrMore(sentenceAndOffence)) {
        SDSEarlyReleaseExclusionType.VIOLENT
      } else {
        SDSEarlyReleaseExclusionType.NO
      }
      SDSEarlyReleaseExclusionSchedulePart.NONE -> SDSEarlyReleaseExclusionType.NO
      null -> SDSEarlyReleaseExclusionType.NO
    }
  }

  private data class SDSPlusCheckResult(val sentenceAndOffence: SentenceAndOffence, val isSDSPlus: Boolean)
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val SDS_AND_DYOI_POST_PCSC_CALC_TYPES = EnumSet.of(
      SentenceCalculationType.ADIMP,
      SentenceCalculationType.ADIMP_ORA,
      SentenceCalculationType.YOI,
      SentenceCalculationType.YOI_ORA,
    )
    private val S250_POST_PCSC_CALC_TYPES = EnumSet.of(
      SentenceCalculationType.SEC250,
      SentenceCalculationType.SEC250_ORA,
    )
    private val RECALL_SENTENCE_TYPES = EnumSet.of(
      SentenceCalculationType.LR,
      SentenceCalculationType.LR_ORA,
      SentenceCalculationType.LR_YOI_ORA,
      SentenceCalculationType.LR_SEC91_ORA,
      SentenceCalculationType.LRSEC250_ORA,
      SentenceCalculationType.FTR_14_ORA,
      SentenceCalculationType.FTR_14_HDC_ORA,
      SentenceCalculationType.FTR,
      SentenceCalculationType.FTR_ORA,
      SentenceCalculationType.FTR_HDC_ORA,
      SentenceCalculationType.FTR_SCH15,
      SentenceCalculationType.FTRSCH15_ORA,
      SentenceCalculationType.FTRSCH18,
      SentenceCalculationType.FTRSCH18_ORA,
      SentenceCalculationType.FTR_HDC,
      SentenceCalculationType.HDR,
      SentenceCalculationType.HDR_ORA,
    )

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
    private val SDS_EXCLUSION_CALC_TYPES = SDS_AND_DYOI_POST_PCSC_CALC_TYPES + S250_POST_PCSC_CALC_TYPES +
      listOf(SentenceCalculationType.SEC91_03, SentenceCalculationType.SEC91_03_ORA) + RECALL_SENTENCE_TYPES
  }
}
