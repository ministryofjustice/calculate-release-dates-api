package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

interface CalculableSentence : SentenceTimeline {
  var releaseDateTypes: List<ReleaseDateType>
  var sentenceCalculation: SentenceCalculation
  val recallType: RecallType?

  @JsonIgnore
  fun isRecall(): Boolean {
    return recallType != null
  }

  @JsonIgnore
  fun getReleaseDateType(): ReleaseDateType {
    return if (isRecall())
      ReleaseDateType.PRRD else if (releaseDateTypes.contains(ReleaseDateType.PED) && this is StandardDeterminate)
      ReleaseDateType.PED else if (sentenceCalculation.isReleaseDateConditional)
      ReleaseDateType.CRD else
      ReleaseDateType.ARD
  }

  fun buildString(): String
}
