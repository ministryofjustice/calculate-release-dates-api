package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelopeSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.OperativeSentenceEnvelopeRepository
import java.time.temporal.ChronoUnit
import kotlin.jvm.optionals.getOrNull

@Component
class OperativeSentenceEnvelopeService(
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val operativeSentenceEnvelopeRepository: OperativeSentenceEnvelopeRepository,
) {

  @Transactional(readOnly = true)
  fun operativeSentenceEnvelopeForPrisoner(prisonerId: String): Either<String, OperativeSentenceEnvelope> = getLatestBookingFromPrisoner(prisonerId)
    .flatMap { bookingId -> prisonService.getOffenderKeyDates(bookingId).map { bookingId to it } }
    .map { (bookingId, prisonerCalculation) ->
      val latestCrdsCalc = calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId).getOrNull()
      val latestIsACrdsCalc = latestCrdsCalc != null && isSameCalc(prisonerCalculation, latestCrdsCalc)
      val persistedSentenceEnvelope = if (latestIsACrdsCalc) operativeSentenceEnvelopeRepository.findByCalculationRequestId(latestCrdsCalc.id()) else null
      return when {
        latestIsACrdsCalc && persistedSentenceEnvelope != null -> persistedSentenceEnvelope.toOperativeSentenceEnvelope().right()
        else -> deriveFromNomis(bookingId, prisonerCalculation)
      }
    }

  private fun deriveFromNomis(bookingId: Long, prisonerCalculation: OffenderKeyDates): Either<String, OperativeSentenceEnvelope> {
    val earliestSentenceDate = prisonService.getEarliestSentenceDate(bookingId)
    val expiryDate = prisonerCalculation.sentenceExpiryDate
    return if (earliestSentenceDate == null) {
      "Missing sentence date for booking ($bookingId)".left()
    } else if (expiryDate == null) {
      "Missing expiry date for booking ($bookingId)".left()
    } else {
      OperativeSentenceEnvelope(
        sentenceEnvelopeLengthInDays = ChronoUnit.DAYS.between(earliestSentenceDate, expiryDate) + 1, // start and end date should be inclusive
        earliestSentenceStartDate = earliestSentenceDate,
        isPostRecallSentenceEnvelope = null,
        containsAnSDSPlusSentence = null,
        sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.NOMIS,
      ).right()
    }
  }

  private fun isSameCalc(prisonerCalculation: OffenderKeyDates, latestCrdsCalc: CalculationRequest): Boolean = when {
    prisonerCalculation.comment == null -> false
    prisonerCalculation.comment.contains(latestCrdsCalc.calculationReference.toString()) -> true
    else -> false
  }

  private fun getLatestBookingFromPrisoner(prisonerId: String): Either<String, Long> = try {
    prisonService.getOffenderDetail(prisonerId).bookingId.right()
  } catch (e: WebClientResponseException) {
    if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
      "Prisoner ($prisonerId) could not be found".left()
    } else {
      e.printStackTrace()
      throw e
    }
  }
}
