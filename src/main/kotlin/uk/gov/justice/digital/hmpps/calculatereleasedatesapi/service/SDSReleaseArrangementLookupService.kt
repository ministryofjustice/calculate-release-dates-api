package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSPlusCheckResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionSchedulePart
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

  internal fun offenceCodesExcludingSDSPlus(checkedForSDSPlus: List<SDSPlusCheckResult>): List<String> =
    checkedForSDSPlus
      .filterNot { it.isSDSPlus }
      .map { it.sentenceAndOffence.offence.offenceCode }.sorted()
  internal fun exclusionForOffence(
    exclusionsForOffences: Map<String, SDSEarlyReleaseExclusionForOffenceCode>,
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
      SDSEarlyReleaseExclusionSchedulePart.SEXUAL_T3,
      SDSEarlyReleaseExclusionSchedulePart.SEXUAL,
      -> SDSEarlyReleaseExclusionType.SEXUAL

      SDSEarlyReleaseExclusionSchedulePart.DOMESTIC_ABUSE_T3,
      SDSEarlyReleaseExclusionSchedulePart.DOMESTIC_ABUSE,
      -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE

      SDSEarlyReleaseExclusionSchedulePart.NATIONAL_SECURITY_T3,
      SDSEarlyReleaseExclusionSchedulePart.NATIONAL_SECURITY,
      -> SDSEarlyReleaseExclusionType.NATIONAL_SECURITY

      SDSEarlyReleaseExclusionSchedulePart.TERRORISM_T3,
      SDSEarlyReleaseExclusionSchedulePart.TERRORISM,
      -> SDSEarlyReleaseExclusionType.TERRORISM

      SDSEarlyReleaseExclusionSchedulePart.MURDER_T3 -> SDSEarlyReleaseExclusionType.MURDER_T3

      SDSEarlyReleaseExclusionSchedulePart.VIOLENT_T3 -> evaluateViolentExclusion(sentenceAndOffence, true)
      SDSEarlyReleaseExclusionSchedulePart.VIOLENT -> evaluateViolentExclusion(sentenceAndOffence, false)

      SDSEarlyReleaseExclusionSchedulePart.NONE -> SDSEarlyReleaseExclusionType.NO
    }
  }

  private fun evaluateViolentExclusion(
    sentenceAndOffence: SentenceAndOffence,
    isT3: Boolean,
  ): SDSEarlyReleaseExclusionType {
    return if (fourYearsOrMore(sentenceAndOffence)) {
      if (isT3) SDSEarlyReleaseExclusionType.VIOLENT_T3 else SDSEarlyReleaseExclusionType.VIOLENT
    } else {
      SDSEarlyReleaseExclusionType.NO
    }
  }
}
