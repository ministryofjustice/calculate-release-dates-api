package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class AdjustmentsService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val sourceDataMapper: SourceDataMapper,
  private val prisonService: PrisonService,
  private val adjustmentsApiClient: AdjustmentsApiClient,
) {

  fun getAnalysedAdjustments(prisonerId: String): List<AnalysedAdjustment> {
    val adjustments = adjustmentsApiClient.getAdjustmentsByPerson(prisonerId, null, listOf(AdjustmentDto.Status.ACTIVE))

    return calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId).map { calculationRequest ->
      if (calculationRequest.adjustments == null) {
        adjustments.map { AdjustmentAnalysisResult.NEW.adjustment(it) }
      } else {
        val lastAdjustments = sourceDataMapper.mapAdjustments(calculationRequest)
        adjustments.map {
          if (lastAdjustments.any { inner -> inner.adjustmentType == it.adjustmentType && inner.days == it.days }) {
            AdjustmentAnalysisResult.SAME.adjustment(it)
          } else {
            AdjustmentAnalysisResult.NEW.adjustment(it)
          }
        }
      }
    }.orElse(adjustments.map { AdjustmentAnalysisResult.NEW.adjustment(it) })
  }

  fun getAnalysedBookingAndSentenceAdjustments(bookingId: Long): AnalysedBookingAndSentenceAdjustments {
    val bookingAndSentenceAdjustments = prisonService.getBookingAndSentenceAdjustments(bookingId)

    return calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(bookingId).map {
      if (it.adjustments == null) {
        return@map newAnalysedBookingAndSentenceAdjustments(bookingAndSentenceAdjustments)
      }
      val lastAdjustments = sourceDataMapper.mapBookingAndSentenceAdjustments(it)
      val analysedBookingAdjustment: List<AnalysedBookingAdjustment> =
        bookingAndSentenceAdjustments.bookingAdjustments.map { bookingAdjustment ->
          val analysisResult = if (lastAdjustments.bookingAdjustments.contains(bookingAdjustment)) AnalysedBookingAndSentenceAdjustmentAnalysisResult.SAME else AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW
          AnalysedBookingAdjustment(
            bookingAdjustment.active,
            bookingAdjustment.fromDate,
            bookingAdjustment.toDate,
            bookingAdjustment.numberOfDays,
            bookingAdjustment.type,
            analysisResult,
          )
        }
      val analysedSentenceAdjustments: List<AnalysedSentenceAdjustment> =
        bookingAndSentenceAdjustments.sentenceAdjustments.map { sentenceAdjustment ->
          val analysisResult = if (lastAdjustments.sentenceAdjustments.contains(sentenceAdjustment)) AnalysedBookingAndSentenceAdjustmentAnalysisResult.SAME else AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW
          AnalysedSentenceAdjustment(
            sentenceAdjustment.sentenceSequence,
            sentenceAdjustment.active,
            sentenceAdjustment.fromDate,
            sentenceAdjustment.toDate,
            sentenceAdjustment.numberOfDays,
            sentenceAdjustment.type,
            analysisResult,
          )
        }
      AnalysedBookingAndSentenceAdjustments(analysedBookingAdjustment, analysedSentenceAdjustments)
    }.orElse(
      newAnalysedBookingAndSentenceAdjustments(bookingAndSentenceAdjustments),
    )
  }

  private fun newAnalysedBookingAndSentenceAdjustments(bookingAndSentenceAdjustments: BookingAndSentenceAdjustments): AnalysedBookingAndSentenceAdjustments {
    val bookingAdjustment = bookingAndSentenceAdjustments.bookingAdjustments.map {
      AnalysedBookingAdjustment(
        it.active,
        it.fromDate,
        it.toDate,
        it.numberOfDays,
        it.type,
        AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW,
      )
    }
    val sentenceAdjustments = bookingAndSentenceAdjustments.sentenceAdjustments.map {
      AnalysedSentenceAdjustment(
        it.sentenceSequence,
        it.active,
        it.fromDate,
        it.toDate,
        it.numberOfDays,
        it.type,
        AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW,
      )
    }
    return AnalysedBookingAndSentenceAdjustments(bookingAdjustment, sentenceAdjustments)
  }
}
