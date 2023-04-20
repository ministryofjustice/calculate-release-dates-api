package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import java.time.LocalDate

/**
 * This class tracks iterative data as we walk through the timeline.
 */
data class TimelineTracker(
  val firstSentence: CalculableSentence,
  var timelineRange: LocalDateRange,
  var previousSentence: CalculableSentence,
  // Which sentences are in the same "Group". A "Group" of sentences are sentences that are concurrent to each other,
  // and there sentenceAt dates overlap with the other release date. i.e. there is no release inbetween them
  // or there is no transition from recall sentences to a parallel sentence.
  // Sentences in a group share adjustments.
  // Whenever a release happens a new group is started.
  val sentenceGroups: MutableList<MutableList<CalculableSentence>> = mutableListOf(),
  var currentSentenceGroup: MutableList<CalculableSentence> = mutableListOf(),
  var previousReleaseDateReached: LocalDate? = null,
)
