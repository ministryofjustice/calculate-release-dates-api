package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType

data class SDSDescriptions(
  @Schema(description = "Any reason this sentence might be excluded from SDS40", nullable = true)
  val sds40ExclusionDescription: String?,
  @Schema(description = "DEPRECATED - Use progressionModelDoesNotApplyDescription and progressionModelExcludedOffenceDescription", nullable = true, deprecated = true)
  val progressionModelExclusionDescription: String?,
  @Schema(description = "Any reason this sentence might be excluded from Progression Model", nullable = true)
  val progressionModelDoesNotApplyDescription: String?,
  @Schema(description = "If this offence is excluded from progression model", nullable = true)
  val progressionModelExcludedOffenceDescription: String?,
  @Schema(description = "The way to display SDS plus status for the sentence", allowableValues = ["SDS+", "YOI+", "S250+"], nullable = true)
  val sdsPlusDisplayName: String?,
) {
  companion object {
    fun from(sentenceAndOffence: SentenceAndOffenceWithReleaseArrangements): SDSDescriptions? {
      val releaseArrangements = sentenceAndOffence.sdsReleaseArrangements
      val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)
      return if (releaseArrangements != null && sentenceCalculationType.isSDS()) {
        val progressionModelDoesNotApplyDescription = if (sentenceCalculationType == SentenceCalculationType.SEC91_03 || sentenceCalculationType == SentenceCalculationType.SEC91_03_ORA) {
          "Section 91"
        } else if (sentenceCalculationType.isSection250()) {
          "Section 250"
        } else if (SDSEarlyReleaseExclusionType.PROGRESSION_MODEL_SCHEDULE_13_PART_3 in releaseArrangements.sdsEarlyReleaseExclusions) {
          SDSEarlyReleaseExclusionType.PROGRESSION_MODEL_SCHEDULE_13_PART_3.displayName
        } else if (releaseArrangements.wouldBeSDSPlusIfSentencedToday()) {
          "Would be ${sentenceCalculationType.sdsPlusDisplayName}"
        } else {
          null
        }
        val progressionModelExcludedOffenceDescription = if (SDSEarlyReleaseExclusionType.SA2026_PROGRESSION_MODEL_SCHEDULE in releaseArrangements.sdsEarlyReleaseExclusions) {
          SDSEarlyReleaseExclusionType.SA2026_PROGRESSION_MODEL_SCHEDULE.displayName
        } else {
          null
        }
        SDSDescriptions(
          sds40ExclusionDescription = releaseArrangements.sdsEarlyReleaseExclusions.firstOrNull { it.sds40Exclusion || it.sds40AdditionalExcludedOffence }?.displayName,
          progressionModelExclusionDescription = progressionModelDoesNotApplyDescription ?: progressionModelExcludedOffenceDescription,
          progressionModelDoesNotApplyDescription = progressionModelDoesNotApplyDescription,
          progressionModelExcludedOffenceDescription = progressionModelExcludedOffenceDescription,
          sdsPlusDisplayName = if (releaseArrangements.isSDSPlus) sentenceCalculationType.sdsPlusDisplayName else null,
        )
      } else {
        null
      }
    }
  }
}
