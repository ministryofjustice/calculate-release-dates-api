package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo

@Service
class RelevantRemandService(
  private val prisonService: PrisonService,
  private val calculationService: CalculationService,
  private val bookingService: BookingService
) {

  fun relevantRemandCalculation(prisonerId: String, request: RelevantRemandCalculationRequest): RelevantRemandCalculationResult {
    val prisoner = prisonService.getOffenderDetail(prisonerId)
    val sourceData = filterSentencesAndAdjustmentsForRelevantRemandCalc(prisonService.getPrisonApiSourceData(prisoner, false), request)
    val calculationUserInputs = CalculationUserInputs(useOffenceIndicators = true)

    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    val calculationResult = calculationService.calculateReleaseDates(booking).second
    return RelevantRemandCalculationResult(if (calculationResult.dates[ReleaseDateType.CRD] != null) calculationResult.dates[ReleaseDateType.CRD]!! else calculationResult.dates[ReleaseDateType.ARD]!!)
  }

  private fun filterSentencesAndAdjustmentsForRelevantRemandCalc(sourceData: PrisonApiSourceData, request: RelevantRemandCalculationRequest): PrisonApiSourceData {
    return sourceData.copy(
      sentenceAndOffences = sourceData.sentenceAndOffences.filter { it.sentenceDate.isBeforeOrEqualTo(request.sentenceDate) },
      bookingAndSentenceAdjustments = sourceData.bookingAndSentenceAdjustments.copy(
        sentenceAdjustments = sourceData.bookingAndSentenceAdjustments.sentenceAdjustments.filter { it.type != SentenceAdjustmentType.REMAND } +
          request.relevantRemands.map { SentenceAdjustment(it.sentenceSequence, true, it.from, it.to, it.days, SentenceAdjustmentType.REMAND) }
      )
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}