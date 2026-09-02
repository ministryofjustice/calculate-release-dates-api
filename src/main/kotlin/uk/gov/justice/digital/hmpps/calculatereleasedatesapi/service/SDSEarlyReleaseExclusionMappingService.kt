package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffenceSdsExclusionIndicator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period

@Service
class SDSEarlyReleaseExclusionMappingService(private val sdsLegislationConfiguration: SDSLegislationConfiguration, private val featureToggles: FeatureToggles) {

  internal fun exclusionForOffence(
    exclusionsForOffence: List<OffenceSdsExclusionIndicator>,
    sentenceAndOffence: SentenceAndOffence,
  ): List<SDSEarlyReleaseExclusionType> {
    if (exclusionsForOffence.isEmpty()) {
      return emptyList()
    }
    return exclusionsForOffence.mapNotNull {
      when (it) {
        OffenceSdsExclusionIndicator.SEXUAL -> SDSEarlyReleaseExclusionType.SEXUAL
        OffenceSdsExclusionIndicator.SEXUAL_T3 -> SDSEarlyReleaseExclusionType.SEXUAL_T3
        OffenceSdsExclusionIndicator.DOMESTIC_ABUSE -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE
        OffenceSdsExclusionIndicator.DOMESTIC_ABUSE_T3 -> SDSEarlyReleaseExclusionType.DOMESTIC_ABUSE_T3
        OffenceSdsExclusionIndicator.NATIONAL_SECURITY -> SDSEarlyReleaseExclusionType.NATIONAL_SECURITY
        OffenceSdsExclusionIndicator.TERRORISM -> SDSEarlyReleaseExclusionType.TERRORISM
        OffenceSdsExclusionIndicator.MURDER_T3 -> SDSEarlyReleaseExclusionType.MURDER_T3
        OffenceSdsExclusionIndicator.VIOLENT -> evaluateViolentExclusion(sentenceAndOffence)
        OffenceSdsExclusionIndicator.SCHEDULE_13_PART_3 -> evaluateSchedule13Part3Exclusion(sentenceAndOffence)
        OffenceSdsExclusionIndicator.SENTENCING_ACT_2026_PROGRESSION_MODEL -> if (featureToggles.progressionModelScheduleExclusionEnabled) SDSEarlyReleaseExclusionType.SA2026_PROGRESSION_MODEL_SCHEDULE else null
        OffenceSdsExclusionIndicator.NONE -> null
      }
    }
  }

  private fun evaluateViolentExclusion(
    sentenceAndOffence: SentenceAndOffence,
  ): SDSEarlyReleaseExclusionType? = if (fourYearsOrMore(sentenceAndOffence)) {
    SDSEarlyReleaseExclusionType.VIOLENT
  } else {
    null
  }

  private fun evaluateSchedule13Part3Exclusion(
    sentenceAndOffence: SentenceAndOffence,
  ): SDSEarlyReleaseExclusionType? = if (sdsLegislationConfiguration.progressionModelLegislation != null && sentenceAndOffence.sentenceDate.isBefore(sdsLegislationConfiguration.progressionModelLegislation.commencementDate())) {
    SDSEarlyReleaseExclusionType.PROGRESSION_MODEL_SCHEDULE_13_PART_3
  } else {
    null
  }

  private fun fourYearsOrMore(sentence: SentenceAndOffence): Boolean {
    val endOfSentenceOrNullIfNoTerms = endOfSentenceOrNullIfNoTerms(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    return endOfSentenceOrNullIfNoTerms != null && endOfSentenceOrNullIfNoTerms.isAfterOrEqualTo(endOfFourYears)
  }

  private fun endOfSentenceOrNullIfNoTerms(sentence: SentenceAndOffence): LocalDate? = if (sentence.terms.isEmpty()) {
    null
  } else {
    val custodialTerm = sentence.terms.first() // there should only be a custodial term for SDS, other numbers of terms will be handled by validation.
    val duration = Period.of(custodialTerm.years, custodialTerm.months, custodialTerm.weeks * 7 + custodialTerm.days)
    sentence.sentenceDate.plus(duration)
  }
}
