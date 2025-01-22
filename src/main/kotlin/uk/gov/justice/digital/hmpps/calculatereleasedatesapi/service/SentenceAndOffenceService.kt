package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceAnalysis
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class SentenceAndOffenceService(
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val prisonApiDataMapper: PrisonApiDataMapper,
) {

  fun getSentencesAndOffences(bookingId: Long): List<AnalysedSentenceAndOffence> {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)

    return calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(bookingId)
      .map { latestCalculation -> determineSentencesAndOffences(sentencesAndOffences, latestCalculation) }
      .orElse(transform(SentenceAndOffenceAnalysis.NEW, sentencesAndOffences))
  }

  fun determineSentencesAndOffences(
    sentencesAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
    latestCalculation: CalculationRequest,
  ): List<AnalysedSentenceAndOffence> {
    if (latestCalculation.sentenceAndOffences == null) {
      return transform(SentenceAndOffenceAnalysis.NEW, sentencesAndOffences)
    }
    val lastSentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements> = prisonApiDataMapper.mapSentencesAndOffences(latestCalculation)

    return if (sentencesAndOffences == lastSentenceAndOffences) {
      transform(SentenceAndOffenceAnalysis.SAME, sentencesAndOffences)
    } else {
      val sentencesAndOffencesBySequence = sentencesAndOffences.groupBy { sentenceAndOffences -> "${sentenceAndOffences.caseSequence}-${sentenceAndOffences.sentenceSequence}" }
      val lastSentencesAndOffencesBySequence = lastSentenceAndOffences.groupBy { sentenceAndOffences -> "${sentenceAndOffences.caseSequence}-${sentenceAndOffences.sentenceSequence}" }
      sentencesAndOffencesBySequence.flatMap { (key: String, values: List<SentenceAndOffenceWithReleaseArrangements>) ->
        if (lastSentencesAndOffencesBySequence.containsKey(key)) {
          if (values.map { it.offence } == lastSentencesAndOffencesBySequence[key]!!.map { it.offence }) {
            values.map { transform(SentenceAndOffenceAnalysis.SAME, it) }
          } else {
            values.map { transform(SentenceAndOffenceAnalysis.UPDATED, it) }
          }
        } else {
          values.map { transform(SentenceAndOffenceAnalysis.NEW, it) }
        }
      }
    }
  }
}
