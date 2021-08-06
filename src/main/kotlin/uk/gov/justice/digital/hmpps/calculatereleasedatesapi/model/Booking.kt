package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class Booking(
  var offender: Offender,
  var sentences: MutableList<Sentence>
)
