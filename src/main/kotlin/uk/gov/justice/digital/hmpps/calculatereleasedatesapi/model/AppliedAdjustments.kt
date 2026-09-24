package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "adjustment details for a release date type")
data class AppliedAdjustments(
  @Schema(description = "the amount of awarded days applied for this specific release date")
  val awarded: Long,
) {
  companion object {
    fun forInitialRelease(sentence: CalculableSentence): AppliedAdjustments = forInitialRelease(sentence.sentenceCalculation.adjustments)
    fun forInitialRelease(sentenceAdjustments: SentenceAdjustments): AppliedAdjustments = AppliedAdjustments(
      sentenceAdjustments.awardedDuringCustody - sentenceAdjustments.servedAdaDays,
    )
  }
}
