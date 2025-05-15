package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import java.time.LocalDate

fun ConsecutiveSentence.hasSentencesBeforeAndAfter(date: LocalDate): Boolean {
  var hasBeforeDate = false
  var hasAfterOrEqualDate = false

  for (sentence in this.sentenceParts()) {
    if (!hasBeforeDate && sentence.sentencedAt.isBefore(date)) hasBeforeDate = true
    if (!hasAfterOrEqualDate && sentence.sentencedAt.isAfterOrEqualTo(date)) hasAfterOrEqualDate = true
    if (hasBeforeDate && hasAfterOrEqualDate) return true
  }
  return false
}
