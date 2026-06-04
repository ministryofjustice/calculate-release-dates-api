package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType

data class SDSDescriptions(
  @Schema(description = "Any reason this sentence might be excluded from SDS40", nullable = true)
  val sds40ExclusionDescription: String?,
  @Schema(description = "Any reason this sentence might be excluded from Progression Model", nullable = true)
  val progressionModelExclusionDescription: String?,
  @Schema(description = "The way to display SDS plus status for the sentence", allowableValues = ["SDS+", "YOI+", "S250+"], nullable = true)
  val sdsPlusDisplayName: String?,
) {
  companion object {
    fun from(sentenceAndOffence: SentenceAndOffenceWithReleaseArrangements): SDSDescriptions? {
      val releaseArrangements = sentenceAndOffence.sdsReleaseArrangements
      val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)
      return if (releaseArrangements != null && sentenceCalculationType.isSDS()) {
        val progressionModelExclusionDescription = if (sentenceCalculationType == SentenceCalculationType.SEC91_03 || sentenceCalculationType == SentenceCalculationType.SEC91_03_ORA) {
          "Section 91"
        } else if (sentenceCalculationType.isSection250()) {
          "Section 250"
        } else if (releaseArrangements.hasProgressionModelExclusion()) {
          releaseArrangements.sdsEarlyReleaseExclusions.firstOrNull { it.progressionModelExclusion }?.displayName
        } else if (releaseArrangements.wouldBeSDSPlusIfSentencedToday()) {
          "Would be ${sentenceCalculationType.sdsPlusDisplayName}"
        } else {
          null
        }
        SDSDescriptions(
          sds40ExclusionDescription = releaseArrangements.sdsEarlyReleaseExclusions.firstOrNull { it.sds40Exclusion || it.sds40AdditionalExcludedOffence }?.displayName,
          progressionModelExclusionDescription = progressionModelExclusionDescription,
          sdsPlusDisplayName = if (releaseArrangements.isSDSPlus) sentenceCalculationType.sdsPlusDisplayName else null,
        )
      } else {
        null
      }
    }
  }
}
