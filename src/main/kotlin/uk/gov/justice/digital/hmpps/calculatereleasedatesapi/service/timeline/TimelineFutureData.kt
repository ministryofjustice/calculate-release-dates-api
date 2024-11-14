package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence

data class TimelineFutureData(
  var remand: List<Adjustment>,
  var taggedBail: List<Adjustment>,
  var recallRemand: List<Adjustment>,
  var recallTaggedBail: List<Adjustment>,
  var additional: List<Adjustment>,
  var restored: List<Adjustment>,
  var ual: List<Adjustment>,
  var sentences: List<CalculableSentence>,
)
