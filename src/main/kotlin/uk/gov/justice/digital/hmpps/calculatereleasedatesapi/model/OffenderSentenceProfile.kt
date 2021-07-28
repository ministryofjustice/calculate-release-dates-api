package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class OffenderSentenceProfile(
  var offender: Offender,
  var sentences: MutableList<Sentence>
)
