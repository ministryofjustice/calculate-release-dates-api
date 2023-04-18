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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo

@Service
class RelevantRemandService(
  private val prisonService: PrisonService,
  private val calculationService: CalculationService,
  private val bookingService: BookingService
) {

  fun relevantRemandCalculation(prisonerId: String, request: RelevantRemandCalculationRequest): RelevantRemandCalculationResult {
    val prisoner = prisonService.getOffenderDetail(prisonerId).copy(
      bookingId = request.sentence.bookingId
    )
    val sourceData = filterSentencesAndAdjustmentsForRelevantRemandCalc(prisonService.getPrisonApiSourceData(prisoner, false), request)
    val calculationUserInputs = CalculationUserInputs(useOffenceIndicators = true)

    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    val calculationResult = calculationService.calculateReleaseDates(booking).second
    val releaseDateTypes = listOf(ReleaseDateType.CRD, ReleaseDateType.ARD, ReleaseDateType.PRRD, ReleaseDateType.MTD)
    return RelevantRemandCalculationResult(
      calculationResult.dates.filter { releaseDateTypes.contains(it.key) }.minOf { it.value }
    )
  }

  private fun filterSentencesAndAdjustmentsForRelevantRemandCalc(sourceData: PrisonApiSourceData, request: RelevantRemandCalculationRequest): PrisonApiSourceData {
    return sourceData.copy(
      sentenceAndOffences = sourceData.sentenceAndOffences.filter { it.sentenceDate.isBeforeOrEqualTo(request.sentence.sentenceDate) },
      bookingAndSentenceAdjustments = sourceData.bookingAndSentenceAdjustments.copy(
        sentenceAdjustments = sourceData.bookingAndSentenceAdjustments.sentenceAdjustments.filter { !listOf(SentenceAdjustmentType.REMAND, SentenceAdjustmentType.RECALL_SENTENCE_REMAND, SentenceAdjustmentType.UNUSED_REMAND).contains(it.type) } +
          request.relevantRemands.map {
            val sentence = findSentence(sourceData.sentenceAndOffences, it.sentenceSequence)
            val adjustmentType: SentenceAdjustmentType = if (sentence != null) {
              val sentenceType = SentenceCalculationType.from(sentence.sentenceCalculationType)
              if (sentenceType.recallType != null) {
                SentenceAdjustmentType.RECALL_SENTENCE_REMAND
              } else {
                SentenceAdjustmentType.REMAND
              }
            } else {
              SentenceAdjustmentType.REMAND
            }
            SentenceAdjustment(it.sentenceSequence, true, it.from, it.to, it.days, adjustmentType)
          }
      )
    )
  }

  private fun findSentence(sentenceAndOffences: List<SentenceAndOffences>, sentenceSequence: Int): SentenceAndOffences? {
    return sentenceAndOffences.find { it.sentenceSequence == sentenceSequence }
  }
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
