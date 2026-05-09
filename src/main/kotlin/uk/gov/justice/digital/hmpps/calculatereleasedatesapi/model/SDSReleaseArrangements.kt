package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class SDSReleaseArrangements(
  val isSDSPlus: Boolean,
  val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean,
  val sdsEarlyReleaseExclusions: List<SDSEarlyReleaseExclusionType>,
  val isSection250: Boolean,
) {
  fun hasSDS40EarlyReleaseExclusion(): Boolean = sdsEarlyReleaseExclusions.any { it.sds40Exclusion }
  fun hasSDS40AdditionalExcludedOffences(): Boolean = sdsEarlyReleaseExclusions.any { it.sds40AdditionalExcludedOffence }
  fun hasProgressionModelExclusion(): Boolean = sdsEarlyReleaseExclusions.any { it.progressionModelExclusion }
  fun wouldBeSDSPlusIfSentencedToday(): Boolean = !isSDSPlus && isSDSPlusEligibleSentenceTypeLengthAndOffence

  companion object {
    fun default() = SDSReleaseArrangements(isSDSPlus = false, isSDSPlusEligibleSentenceTypeLengthAndOffence = false, sdsEarlyReleaseExclusions = emptyList(), isSection250 = false)
  }
}
