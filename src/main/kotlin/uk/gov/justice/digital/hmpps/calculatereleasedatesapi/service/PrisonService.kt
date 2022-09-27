package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates

@Service
class PrisonService(
  private val prisonApiClient: PrisonApiClient,
) {

  fun getPrisonApiSourceDataIncludingInactive(prisonerId: String): PrisonApiSourceData {
    val prisonerDetails = getOffenderDetail(prisonerId)
    return if (prisonerDetails.agencyId == "OUT") {
      getInactivePrisonApiSourceData(prisonerDetails)
    } else {
      getActivePrisonApiSourceData(prisonerDetails)
    }
  }
  fun getPrisonApiSourceData(prisonerId: String): PrisonApiSourceData {
    val prisonerDetails = getOffenderDetail(prisonerId)
    return getActivePrisonApiSourceData(prisonerDetails)
  }

  private fun getActivePrisonApiSourceData(prisonerDetails: PrisonerDetails): PrisonApiSourceData {
    val sentenceAndOffences = getSentencesAndOffences(prisonerDetails.bookingId)
    val bookingAndSentenceAdjustments = getBookingAndSentenceAdjustments(prisonerDetails.bookingId)
    val bookingHasFixedTermRecall = sentenceAndOffences.any { SentenceCalculationType.from(it.sentenceCalculationType)?.recallType?.isFixedTermRecall == true }
    var returnToCustodyDate: ReturnToCustodyDate? = null
    if (bookingHasFixedTermRecall) {
      returnToCustodyDate = prisonApiClient.getReturnToCustodyDate(prisonerDetails.bookingId)
    }
    val bookingHasAFine = sentenceAndOffences.any { SentenceCalculationType.from(it.sentenceCalculationType)?.sentenceClazz == AFineSentence::class.java }
    var offenderFinePayments: List<OffenderFinePayment> = listOf()
    if (bookingHasAFine) {
      offenderFinePayments = getOffenderFinePayments(prisonerDetails.bookingId)
    }
    return PrisonApiSourceData(sentenceAndOffences, prisonerDetails, bookingAndSentenceAdjustments, offenderFinePayments, returnToCustodyDate)
  }

  private fun getInactivePrisonApiSourceData(prisonerDetails: PrisonerDetails): PrisonApiSourceData {
    val sentenceAndOffences = getSentencesAndOffences(prisonerDetails.bookingId, false)
    val bookingAndSentenceAdjustments = getBookingAndSentenceAdjustments(prisonerDetails.bookingId, false)
    val bookingHasFixedTermRecall = sentenceAndOffences.any { SentenceCalculationType.from(it.sentenceCalculationType)?.recallType?.isFixedTermRecall == true }
    var returnToCustodyDate: ReturnToCustodyDate? = null
    if (bookingHasFixedTermRecall) {
      returnToCustodyDate = prisonApiClient.getReturnToCustodyDate(prisonerDetails.bookingId)
    }
    val bookingHasAFine = sentenceAndOffences.any { SentenceCalculationType.from(it.sentenceCalculationType)?.sentenceClazz == AFineSentence::class.java }
    var offenderFinePayments: List<OffenderFinePayment> = listOf()
    if (bookingHasAFine) {
      offenderFinePayments = prisonApiClient.getOffenderFinePayments(prisonerDetails.bookingId)
    }
    return PrisonApiSourceData(sentenceAndOffences, prisonerDetails, bookingAndSentenceAdjustments, offenderFinePayments, returnToCustodyDate)
  }

  fun getOffenderDetail(prisonerId: String): PrisonerDetails {
    return prisonApiClient.getOffenderDetail(prisonerId)
  }

  fun getBookingAndSentenceAdjustments(bookingId: Long, filterActive: Boolean = true): BookingAndSentenceAdjustments {
    val adjustments = prisonApiClient.getSentenceAndBookingAdjustments(bookingId)
    return BookingAndSentenceAdjustments(
      sentenceAdjustments = adjustments.sentenceAdjustments.filter { !filterActive || it.active },
      bookingAdjustments = adjustments.bookingAdjustments.filter { !filterActive || it.active }
    )
  }

  fun getSentencesAndOffences(bookingId: Long, filterActive: Boolean = true): List<SentenceAndOffences> {
    return prisonApiClient.getSentencesAndOffences(bookingId)
      .filter { !filterActive || it.sentenceStatus == "A" }
  }

  fun getOffenderFinePayments(bookingId: Long): List<OffenderFinePayment> {
    return prisonApiClient.getOffenderFinePayments(bookingId)
  }

  fun postReleaseDates(bookingId: Long, updateOffenderDates: UpdateOffenderDates) {
    return prisonApiClient.postReleaseDates(bookingId, updateOffenderDates)
  }
}
