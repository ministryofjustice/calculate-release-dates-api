package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment

data class BookingAndSentenceAdjustments(
  val bookingAdjustments: List<BookingAdjustment>,
  val sentenceAdjustments: List<SentenceAdjustment>,
)
