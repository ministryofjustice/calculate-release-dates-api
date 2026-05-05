package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

enum class EarlyReleaseSentenceFilter {
  SDS_40_EXCLUSIONS {
    override fun isIncluded(part: AbstractSentence): Boolean = part is StandardDeterminateSentence &&
      part.identificationTrack == SentenceIdentificationTrack.SDS &&
      !part.releaseArrangements.hasSDS40EarlyReleaseExclusion()
  },
  SDS_40_ADDITIONAL_EXCLUDED_OFFENCES {
    override fun isIncluded(part: AbstractSentence): Boolean = part is StandardDeterminateSentence && part.identificationTrack == SentenceIdentificationTrack.SDS && part.releaseArrangements.hasSDS40AdditionalExcludedOffences()
  },
  SDS_PROGRESSION_MODEL {
    override fun isIncluded(part: AbstractSentence): Boolean = part is StandardDeterminateSentence &&
      !part.releaseArrangements.isSection250 &&
      listOf(SentenceIdentificationTrack.SDS, SentenceIdentificationTrack.SDS_PLUS).contains(part.identificationTrack)
  },
  SDS_OR_SDS_PLUS {
    override fun isIncluded(part: AbstractSentence): Boolean = listOf(SentenceIdentificationTrack.SDS, SentenceIdentificationTrack.SDS_PLUS).contains(part.identificationTrack)
  },
  ;

  abstract fun isIncluded(part: AbstractSentence): Boolean
}
