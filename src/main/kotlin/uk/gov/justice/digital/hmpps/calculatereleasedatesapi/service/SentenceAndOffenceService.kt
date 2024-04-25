package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceAnalysis
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class SentenceAndOffenceService(
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val prisonApiDataMapper: PrisonApiDataMapper,
) {

  fun getSentencesAndOffences(bookingId: Long): List<AnalyzedSentenceAndOffences> {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    val lastCalculation = calculationRequestRepository.findLatestCalculation(bookingId)

    return lastCalculation.map {
      if (it.sentenceAndOffences == null) {
        return@map transform(SentenceAndOffenceAnalysis.NEW, sentencesAndOffences)
      }
      val lastSentenceAndOffences: List<SentenceAndOffences> = prisonApiDataMapper.mapSentencesAndOffences(it)
      if (sentencesAndOffences == lastSentenceAndOffences) {
        transform(SentenceAndOffenceAnalysis.SAME, sentencesAndOffences)
      } else {
        val sentencesAndOffencesBySequence = sentencesAndOffences.associateBy { sentenceAndOffences -> "${sentenceAndOffences.caseSequence}-${sentenceAndOffences.sentenceSequence}" }
        val lastSentencesAndOffencesBySequence = lastSentenceAndOffences.associateBy { sentenceAndOffences -> "${sentenceAndOffences.caseSequence}-${sentenceAndOffences.sentenceSequence}" }
        sentencesAndOffencesBySequence.map { (key: String, value: SentenceAndOffences) ->
          if (lastSentencesAndOffencesBySequence.containsKey(key)) {
            if (value.offences == lastSentencesAndOffencesBySequence[key]!!.offences) {
              transform(SentenceAndOffenceAnalysis.SAME, value)
            } else {
              transform(SentenceAndOffenceAnalysis.UPDATED, value)
            }
          } else {
            transform(SentenceAndOffenceAnalysis.NEW, value)
          }
        }
      }
    }.orElse(transform(SentenceAndOffenceAnalysis.NEW, sentencesAndOffences))
  }
}
