package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedBookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedSentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class AdjustmentsService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val prisonService: PrisonService,
) {

  fun getAnalyzedAdjustments(bookingId: Long): AnalyzedBookingAndSentenceAdjustments {
    val bookingAndSentenceAdjustments = prisonService.getBookingAndSentenceAdjustments(bookingId)

    return calculationRequestRepository.findLatestCalculation(bookingId).map {
      val objectMapper = jacksonObjectMapper().findAndRegisterModules()
      val lastAdjustments: BookingAndSentenceAdjustments = objectMapper.readValue(it.adjustments!!.toString())
      val analyzedBookingAdjustment: List<AnalyzedBookingAdjustment> =
        bookingAndSentenceAdjustments.bookingAdjustments.map { bookingAdjustment ->
          val analysisResult = if (lastAdjustments.bookingAdjustments.contains(bookingAdjustment)) AdjustmentAnalysisResult.SAME else AdjustmentAnalysisResult.NEW
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
          val analysisResult = if (lastAdjustments.sentenceAdjustments.contains(sentenceAdjustment)) AdjustmentAnalysisResult.SAME else AdjustmentAnalysisResult.NEW
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
        AdjustmentAnalysisResult.NEW,
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
        AdjustmentAnalysisResult.NEW,
      )
    }
    return AnalyzedBookingAndSentenceAdjustments(bookingAdjustment, sentenceAdjustments)
  }
}
