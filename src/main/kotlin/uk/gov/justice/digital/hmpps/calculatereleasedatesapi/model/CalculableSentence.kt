package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

interface CalculableSentence : SentenceTimeline {
  var releaseDateTypes: List<ReleaseDateType>
  var sentenceCalculation: SentenceCalculation
}
