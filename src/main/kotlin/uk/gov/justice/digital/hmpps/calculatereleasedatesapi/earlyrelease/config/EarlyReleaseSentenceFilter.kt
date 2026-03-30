package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

enum class EarlyReleaseSentenceFilter {
  SDS_40_EXCLUSIONS {
    override fun matches(part: AbstractSentence): Boolean = part is StandardDeterminateSentence &&
      part.identificationTrack == SentenceIdentificationTrack.SDS &&
      (part.hasAnSDSEarlyReleaseExclusion == SDSEarlyReleaseExclusionType.NO || part.hasAnSDSEarlyReleaseExclusion.trancheThreeExclusion)
  },
  SDS_40_ADDITIONAL_EXCLUDED_OFFENCES {
    override fun matches(part: AbstractSentence): Boolean = part is StandardDeterminateSentence && part.identificationTrack == SentenceIdentificationTrack.SDS && part.hasAnSDSEarlyReleaseExclusion.trancheThreeExclusion
  },
  SDS_OR_SDS_PLUS_ADULT {
    override fun matches(part: AbstractSentence): Boolean = part is StandardDeterminateSentence &&
      !part.section250 &&
      listOf(SentenceIdentificationTrack.SDS, SentenceIdentificationTrack.SDS_PLUS).contains(part.identificationTrack)
  },
  SDS_OR_SDS_PLUS {
    override fun matches(part: AbstractSentence): Boolean = listOf(SentenceIdentificationTrack.SDS, SentenceIdentificationTrack.SDS_PLUS).contains(part.identificationTrack)
  },
  YOUTH {
    override fun matches(part: AbstractSentence): Boolean = part is StandardDeterminateSentence && part.section250
  }, ;

  abstract fun matches(part: AbstractSentence): Boolean
}
