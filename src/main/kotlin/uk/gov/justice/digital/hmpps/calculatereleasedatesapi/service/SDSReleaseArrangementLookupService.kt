package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffenceSdsExclusion
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSPlusCheckResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period

@Service
class SDSReleaseArrangementLookupService {

  private fun endOfSentence(sentence: SentenceAndOffence): LocalDate {
    val duration =
      Period.of(sentence.terms[0].years, sentence.terms[0].months, sentence.terms[0].weeks * 7 + sentence.terms[0].days)
    return sentence.sentenceDate.plus(duration)
  }

  private fun fourYearsOrMore(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears)
  }

  internal fun offenceCodesExcludingSDSPlus(checkedForSDSPlus: List<SDSPlusCheckResult>): List<String> = checkedForSDSPlus
    .filterNot { it.isSDSPlus }
    .map { it.sentenceAndOffence.offence.offenceCode }.sorted()

  internal fun exclusionForOffence(
    exclusionsForOffences: Map<String, OffenceSdsExclusion>,
    sentenceAndOffence: SentenceAndOffence,
    isSDSPlus: Boolean,
  ): SDSEarlyReleaseExclusionType {
    if (isSDSPlus) return SDSEarlyReleaseExclusionType.NO
    if (!SentenceCalculationType.isSDS40Eligible(sentenceAndOffence.sentenceCalculationType)) {
      return SDSEarlyReleaseExclusionType.NO
    }

    val offenceCode = sentenceAndOffence.offence.offenceCode
    val exclusionForOffence = exclusionsForOffences[offenceCode] ?: return SDSEarlyReleaseExclusionType.NO

    return when (exclusionForOffence.schedulePart) {
      OffenceSdsExclusion.SchedulePart.SEXUAL_T3,
      OffenceSdsExclusion.SchedulePart.SEXUAL,
      -> SDSEarlyReleaseExclusionType.SEXUAL

      OffenceSdsExclusion.SchedulePart.DOMESTIC_ABUSE_T3,
      OffenceSdsExclusion.SchedulePart.DOMESTIC_ABUSE,
      -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE

      OffenceSdsExclusion.SchedulePart.NATIONAL_SECURITY,
      -> SDSEarlyReleaseExclusionType.NATIONAL_SECURITY

      OffenceSdsExclusion.SchedulePart.TERRORISM,
      -> SDSEarlyReleaseExclusionType.TERRORISM

      OffenceSdsExclusion.SchedulePart.MURDER_T3 -> SDSEarlyReleaseExclusionType.MURDER_T3

      OffenceSdsExclusion.SchedulePart.VIOLENT -> evaluateViolentExclusion(sentenceAndOffence)

      OffenceSdsExclusion.SchedulePart.SCHEDULE_13_PART_3 -> SDSEarlyReleaseExclusionType.SCHEDULE_13_PART_3

      OffenceSdsExclusion.SchedulePart.NONE -> SDSEarlyReleaseExclusionType.NO
    }
  }

  private fun evaluateViolentExclusion(
    sentenceAndOffence: SentenceAndOffence,
  ): SDSEarlyReleaseExclusionType = if (fourYearsOrMore(sentenceAndOffence)) {
    SDSEarlyReleaseExclusionType.VIOLENT
  } else {
    SDSEarlyReleaseExclusionType.NO
  }
}
