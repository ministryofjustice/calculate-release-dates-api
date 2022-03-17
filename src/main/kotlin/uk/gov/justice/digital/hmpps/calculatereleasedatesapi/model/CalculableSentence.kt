package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

interface CalculableSentence : SentenceTimeline {
  var releaseDateTypes: List<ReleaseDateType>
  var sentenceCalculation: SentenceCalculation
  val sentenceType: SentenceType

  @JsonIgnore
  fun getReleaseDateType(): ReleaseDateType {
    return if (sentenceType.isRecall)
      ReleaseDateType.PRRD else if (releaseDateTypes.contains(ReleaseDateType.PED))
      ReleaseDateType.PED else if (sentenceCalculation.isReleaseDateConditional)
      ReleaseDateType.CRD else
      ReleaseDateType.ARD
  }

  fun buildString(): String
}
