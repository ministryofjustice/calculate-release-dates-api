package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceAnalysis
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class SentenceAndOffenceService(
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
) {

  fun getSentencesAndOffences(bookingId: Long): List<AnalyzedSentenceAndOffences> {
    val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    val lastCalculation = calculationRequestRepository.findLatestCalculation(bookingId)

    return lastCalculation.map {
      val lastSentenceAndOffences: List<SentenceAndOffences> = objectMapper.readValue(it.sentenceAndOffences!!.toString())
      if (sentencesAndOffences == lastSentenceAndOffences) {
        return@map transform(SentenceAndOffenceAnalysis.SAME, sentencesAndOffences)
      } else {
        val sentencesAndOffencesBySequence = sentencesAndOffences.associateBy { sentenceAndOffences -> "${sentenceAndOffences.caseSequence}-${sentenceAndOffences.sentenceSequence}" }
        val lastSentencesAndOffencesBySequence = lastSentenceAndOffences.associateBy { sentenceAndOffences -> "${sentenceAndOffences.caseSequence}-${sentenceAndOffences.sentenceSequence}" }
        return@map sentencesAndOffencesBySequence.map { (key: String, value: SentenceAndOffences) ->
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
