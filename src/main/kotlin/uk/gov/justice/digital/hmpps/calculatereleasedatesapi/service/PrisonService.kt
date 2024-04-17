package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.isSupported
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope

@Service
class PrisonService(
  private val prisonApiClient: PrisonApiClient,
) {
  //  The activeDataOnly flag is only used by a test endpoint (1000 calcs test, which is used to test historic data)
  fun getPrisonApiSourceData(prisonerId: String, activeDataOnly: Boolean = true): PrisonApiSourceData {
    val prisonerDetails = getOffenderDetail(prisonerId)
    val activeOnly = activeDataOnly || prisonerDetails.agencyId != "OUT"
    return getPrisonApiSourceData(prisonerDetails, activeOnly)
  }

  fun getPrisonApiSourceData(prisonerDetails: PrisonerDetails, activeDataOnly: Boolean = true): PrisonApiSourceData {
    val sentenceAndOffences = getSentencesAndOffences(prisonerDetails.bookingId, activeDataOnly)
    val bookingAndSentenceAdjustments = getBookingAndSentenceAdjustments(prisonerDetails.bookingId, activeDataOnly)
    val bookingHasFixedTermRecall = sentenceAndOffences.any { isSupported(it.sentenceCalculationType) && from(it.sentenceCalculationType).recallType?.isFixedTermRecall == true }
    val (ftrDetails, returnToCustodyDate) = getFixedTermRecallDetails(prisonerDetails.bookingId, bookingHasFixedTermRecall)
    val bookingHasAFine = sentenceAndOffences.any { isSupported(it.sentenceCalculationType) && from(it.sentenceCalculationType).sentenceClazz == AFineSentence::class.java }
    val offenderFinePayments = if (bookingHasAFine) prisonApiClient.getOffenderFinePayments(prisonerDetails.bookingId) else listOf()

    return PrisonApiSourceData(
      sentenceAndOffences,
      prisonerDetails,
      bookingAndSentenceAdjustments,
      offenderFinePayments,
      returnToCustodyDate,
      ftrDetails,
    )
  }

  private fun getFixedTermRecallDetails(bookingId: Long, bookingHasFixedTermRecall: Boolean): Pair<FixedTermRecallDetails?, ReturnToCustodyDate?> {
    // TODO Remove ReturnToCustodyDate - FixedTermRecallDetails is all that's required. Will be done as tech debt
    if (!bookingHasFixedTermRecall) return Pair(null, null)
    val ftrDetails = prisonApiClient.getFixedTermRecallDetails(bookingId)
    val returnToCustodyDate = transform(ftrDetails)
    return ftrDetails to returnToCustodyDate
  }

  fun getOffenderDetail(prisonerId: String): PrisonerDetails {
    return prisonApiClient.getOffenderDetail(prisonerId)
  }

  fun getBookingAndSentenceAdjustments(bookingId: Long, filterActive: Boolean = true): BookingAndSentenceAdjustments {
    val adjustments = prisonApiClient.getSentenceAndBookingAdjustments(bookingId)
    return BookingAndSentenceAdjustments(
      sentenceAdjustments = adjustments.sentenceAdjustments.filter { !filterActive || it.active },
      bookingAdjustments = adjustments.bookingAdjustments.filter { !filterActive || it.active },
    )
  }

  fun getSentencesAndOffences(bookingId: Long, filterActive: Boolean = true): List<SentenceAndOffences> {
    return prisonApiClient.getSentencesAndOffences(bookingId)
      .filter { !filterActive || it.sentenceStatus == "A" }
  }

  fun postReleaseDates(bookingId: Long, updateOffenderDates: UpdateOffenderDates) {
    return prisonApiClient.postReleaseDates(bookingId, updateOffenderDates)
  }

  fun getCurrentUserPrisonsList(): List<String> {
    return prisonApiClient.getCurrentUserCaseLoads()?.map { caseLoad -> caseLoad.caseLoadId }
      ?: emptyList()
  }

  fun getActiveBookingsByEstablishment(establishmentId: String, token: String): List<CalculableSentenceEnvelope> {
    var isLastPage = false
    var pageNumber = 0
    val calculableSentenceEnvelope = mutableListOf<CalculableSentenceEnvelope>()

    while (!isLastPage) {
      val calculableSentenceEnvelopePage =
        prisonApiClient.getCalculableSentenceEnvelopesByEstablishment(establishmentId, pageNumber, token)
      calculableSentenceEnvelope.addAll(calculableSentenceEnvelopePage.content)
      isLastPage = calculableSentenceEnvelopePage.isLast
      pageNumber++
    }
    return calculableSentenceEnvelope
  }

  fun getActiveBookingsByPrisonerIds(prisonerIds: List<String>, token: String): List<CalculableSentenceEnvelope> = prisonApiClient.getCalculableSentenceEnvelopesByPrisonerIds(prisonerIds, token)

  fun getCalculationsForAPrisonerId(prisonerId: String): List<SentenceCalculationSummary> = prisonApiClient.getCalculationsForAPrisonerId(prisonerId)

  fun getAgenciesByType(type: String) = prisonApiClient.getAgenciesByType(type)

  fun getOffenderKeyDates(bookingId: Long): Either<String, OffenderKeyDates> = prisonApiClient.getOffenderKeyDates(bookingId)
  fun getNOMISCalcReasons(): List<NomisCalculationReason> = prisonApiClient.getNOMISCalcReasons()

  fun getNOMISOffenderKeyDates(offenderSentCalcId: Long): Either<String, OffenderKeyDates> = prisonApiClient.getNOMISOffenderKeyDates(offenderSentCalcId)
}
