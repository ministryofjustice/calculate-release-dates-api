package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceAnalysis
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class SentenceAndOffenceService(
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val prisonApiDataMapper: PrisonApiDataMapper,
) {

  fun getSentencesAndOffences(bookingId: Long): List<AnalysedSentenceAndOffence> {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)

    return calculationRequestRepository.findLatestCalculation(bookingId).map { latestCalculation ->
      if (latestCalculation.sentenceAndOffences == null) {
        return@map transform(SentenceAndOffenceAnalysis.NEW, sentencesAndOffences)
      }
      val lastSentenceAndOffences: List<SentenceAndOffence> = prisonApiDataMapper.mapSentencesAndOffences(latestCalculation)
      if (sentencesAndOffences == lastSentenceAndOffences) {
        transform(SentenceAndOffenceAnalysis.SAME, sentencesAndOffences)
      } else {
        val sentencesAndOffencesBySequence = sentencesAndOffences.groupBy { sentenceAndOffences -> "${sentenceAndOffences.caseSequence}-${sentenceAndOffences.sentenceSequence}" }
        val lastSentencesAndOffencesBySequence = lastSentenceAndOffences.groupBy { sentenceAndOffences -> "${sentenceAndOffences.caseSequence}-${sentenceAndOffences.sentenceSequence}" }
        sentencesAndOffencesBySequence.flatMap { (key: String, values: List<SentenceAndOffence>) ->
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
    }.orElse(transform(SentenceAndOffenceAnalysis.NEW, sentencesAndOffences))
  }
}
