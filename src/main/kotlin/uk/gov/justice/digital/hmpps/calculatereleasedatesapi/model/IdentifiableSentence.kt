package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack

interface IdentifiableSentence : SentenceTimeline {
  val offence: Offence
  var identificationTrack: SentenceIdentificationTrack
  var releaseDateTypes: List<ReleaseDateType>
}
