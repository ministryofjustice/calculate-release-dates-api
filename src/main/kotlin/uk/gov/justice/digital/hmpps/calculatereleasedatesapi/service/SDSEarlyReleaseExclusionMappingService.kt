package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffenceSdsExclusionIndicator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period

@Service
class SDSEarlyReleaseExclusionMappingService {

  internal fun exclusionForOffence(
    exclusionsForOffence: List<OffenceSdsExclusionIndicator>,
    sentenceAndOffence: SentenceAndOffence,
  ): List<SDSEarlyReleaseExclusionType> {
    if (exclusionsForOffence.isEmpty()) {
      return emptyList()
    }
    return exclusionsForOffence.map {
      when (it) {
        OffenceSdsExclusionIndicator.SEXUAL -> SDSEarlyReleaseExclusionType.SEXUAL
        OffenceSdsExclusionIndicator.SEXUAL_T3 -> SDSEarlyReleaseExclusionType.SEXUAL_T3
        OffenceSdsExclusionIndicator.DOMESTIC_ABUSE -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE
        OffenceSdsExclusionIndicator.DOMESTIC_ABUSE_T3 -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE_T3
        OffenceSdsExclusionIndicator.NATIONAL_SECURITY -> SDSEarlyReleaseExclusionType.NATIONAL_SECURITY
        OffenceSdsExclusionIndicator.TERRORISM -> SDSEarlyReleaseExclusionType.TERRORISM
        OffenceSdsExclusionIndicator.MURDER_T3 -> SDSEarlyReleaseExclusionType.MURDER_T3
        OffenceSdsExclusionIndicator.VIOLENT -> evaluateViolentExclusion(sentenceAndOffence)
        OffenceSdsExclusionIndicator.SCHEDULE_13_PART_3 -> SDSEarlyReleaseExclusionType.SCHEDULE_13_PART_3
        OffenceSdsExclusionIndicator.NONE -> SDSEarlyReleaseExclusionType.NO
      }
    }
  }

  private fun evaluateViolentExclusion(
    sentenceAndOffence: SentenceAndOffence,
  ): SDSEarlyReleaseExclusionType = if (fourYearsOrMore(sentenceAndOffence)) {
    SDSEarlyReleaseExclusionType.VIOLENT
  } else {
    SDSEarlyReleaseExclusionType.NO
  }

  private fun fourYearsOrMore(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears)
  }

  private fun endOfSentence(sentence: SentenceAndOffence): LocalDate {
    val duration =
      Period.of(sentence.terms[0].years, sentence.terms[0].months, sentence.terms[0].weeks * 7 + sentence.terms[0].days)
    return sentence.sentenceDate.plus(duration)
  }
}
