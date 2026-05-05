package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class SDSReleaseArrangements(
  val isSDSPlus: Boolean,
  val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean, // would be SDS+ if sentenced today
  val sdsEarlyReleaseExclusions: List<SDSEarlyReleaseExclusionType>,
  val isSection250: Boolean,
) {
  fun hasSDS40EarlyReleaseExclusion(): Boolean = sdsEarlyReleaseExclusions.any { it.sds40Exclusion }
  fun hasSDS40AdditionalExcludedOffences(): Boolean = sdsEarlyReleaseExclusions.any { it.sds40AdditionalExcludedOffence }
  fun hasProgressionModelExclusion(): Boolean = sdsEarlyReleaseExclusions.any { it.progressionModelExclusion }
}
