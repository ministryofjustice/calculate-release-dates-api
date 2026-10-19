package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "SDS legislation that affected a release date")
data class AffectedBySdsLegislation(
  @Schema(description = "whether any part of the sentence was affected by SDS40")
  val sds40: Boolean,
  @Schema(description = "whether any part of the sentence was affected by Progression Model")
  val progressionModel: Boolean,
) {
  companion object {
    fun from(sentence: CalculableSentence): AffectedBySdsLegislation = AffectedBySdsLegislation(
      sds40 = !sentence.isRecall() && sentence.isAffectedBySds40EarlyRelease(),
      progressionModel = !sentence.isRecall() && sentence.isAffectedBySdsProgressionModel(),
    )
  }
}
