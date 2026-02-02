
package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

data class PrisonPeriod(

  /* The book number for this booking */
  val bookNumber: String,

  /* The ID of this booking */
  val bookingId: Long,

  /* The order sequence of this booking */
  val bookingSequence: Int,
)
