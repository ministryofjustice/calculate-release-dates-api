package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData

interface TrancheSelectionStrategy {
  fun hasSentencesThatMightApplyToTheTranche(timelineTrackingData: TimelineTrackingData, legislation: Legislation): Boolean
  fun sentencesToMatchOnSentenceLength(timelineTrackingData: TimelineTrackingData, legislation: Legislation): List<CalculableSentence>
}
