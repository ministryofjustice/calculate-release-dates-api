package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedBookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedBookingAndSentenceAdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedSentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class AdjustmentsService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val sourceDataMapper: SourceDataMapper,
  private val prisonService: PrisonService,
  private val adjustmentsApiClient: AdjustmentsApiClient,
) {

  fun getAnalyzedAdjustments(prisonerId: String): List<AnalyzedAdjustment> {
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

  fun getAnalyzedBookingAndSentenceAdjustments(bookingId: Long): AnalyzedBookingAndSentenceAdjustments {
    val bookingAndSentenceAdjustments = prisonService.getBookingAndSentenceAdjustments(bookingId)

    return calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(bookingId).map {
      if (it.adjustments == null) {
        return@map newAnalyzedBookingAndSentenceAdjustments(bookingAndSentenceAdjustments)
      }
      val lastAdjustments = sourceDataMapper.mapBookingAndSentenceAdjustments(it)
      val analyzedBookingAdjustment: List<AnalyzedBookingAdjustment> =
        bookingAndSentenceAdjustments.bookingAdjustments.map { bookingAdjustment ->
          val analysisResult = if (lastAdjustments.bookingAdjustments.contains(bookingAdjustment)) AnalyzedBookingAndSentenceAdjustmentAnalysisResult.SAME else AnalyzedBookingAndSentenceAdjustmentAnalysisResult.NEW
          AnalyzedBookingAdjustment(
            bookingAdjustment.active,
            bookingAdjustment.fromDate,
            bookingAdjustment.toDate,
            bookingAdjustment.numberOfDays,
            bookingAdjustment.type,
            analysisResult,
          )
        }
      val analyzedSentenceAdjustments: List<AnalyzedSentenceAdjustment> =
        bookingAndSentenceAdjustments.sentenceAdjustments.map { sentenceAdjustment ->
          val analysisResult = if (lastAdjustments.sentenceAdjustments.contains(sentenceAdjustment)) AnalyzedBookingAndSentenceAdjustmentAnalysisResult.SAME else AnalyzedBookingAndSentenceAdjustmentAnalysisResult.NEW
          AnalyzedSentenceAdjustment(
            sentenceAdjustment.sentenceSequence,
            sentenceAdjustment.active,
            sentenceAdjustment.fromDate,
            sentenceAdjustment.toDate,
            sentenceAdjustment.numberOfDays,
            sentenceAdjustment.type,
            analysisResult,
          )
        }
      AnalyzedBookingAndSentenceAdjustments(analyzedBookingAdjustment, analyzedSentenceAdjustments)
    }.orElse(
      newAnalyzedBookingAndSentenceAdjustments(bookingAndSentenceAdjustments),
    )
  }

  private fun newAnalyzedBookingAndSentenceAdjustments(bookingAndSentenceAdjustments: BookingAndSentenceAdjustments): AnalyzedBookingAndSentenceAdjustments {
    val bookingAdjustment = bookingAndSentenceAdjustments.bookingAdjustments.map {
      AnalyzedBookingAdjustment(
        it.active,
        it.fromDate,
        it.toDate,
        it.numberOfDays,
        it.type,
        AnalyzedBookingAndSentenceAdjustmentAnalysisResult.NEW,
      )
    }
    val sentenceAdjustments = bookingAndSentenceAdjustments.sentenceAdjustments.map {
      AnalyzedSentenceAdjustment(
        it.sentenceSequence,
        it.active,
        it.fromDate,
        it.toDate,
        it.numberOfDays,
        it.type,
        AnalyzedBookingAndSentenceAdjustmentAnalysisResult.NEW,
      )
    }
    return AnalyzedBookingAndSentenceAdjustments(bookingAdjustment, sentenceAdjustments)
  }
}
