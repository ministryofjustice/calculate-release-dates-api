package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates

@Service
class PrisonService(
  private val prisonApiClient: PrisonApiClient,
) {

  fun getPrisonApiSourceData(prisonerId: String): PrisonApiSourceData {
    val prisonerDetails = getOffenderDetail(prisonerId)
    val sentenceAndOffences = getSentencesAndOffences(prisonerDetails.bookingId)
    val bookingAndSentenceAdjustments = getBookingAndSentenceAdjustmentss(prisonerDetails.bookingId)
    return PrisonApiSourceData(sentenceAndOffences, prisonerDetails, bookingAndSentenceAdjustments)
  }

  fun getOffenderDetail(prisonerId: String): PrisonerDetails {
    return prisonApiClient.getOffenderDetail(prisonerId)
  }

  fun getBookingAndSentenceAdjustmentss(bookingId: Long): BookingAndSentenceAdjustments {
    val adjustments = prisonApiClient.getSentenceAndBookingAdjustments(bookingId)
    return BookingAndSentenceAdjustments(
      sentenceAdjustments = adjustments.sentenceAdjustments.filter { it.active },
      bookingAdjustments = adjustments.bookingAdjustments.filter { it.active }
    )
  }

  fun getSentencesAndOffences(bookingId: Long): List<SentenceAndOffences> {
    return prisonApiClient.getSentencesAndOffences(bookingId)
      .filter { it.sentenceStatus == "A" }
  }

  fun postReleaseDates(bookingId: Long, updateOffenderDates: UpdateOffenderDates) {
    return prisonApiClient.postReleaseDates(bookingId, updateOffenderDates)
  }
}
