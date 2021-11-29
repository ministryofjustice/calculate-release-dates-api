package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SupportedSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo

@Service
class SentenceValidationService {
  fun validateSupportedSentences(sentenceAndOffences: List<SentenceAndOffences>) {
    val unsupportedSentences = sentenceAndOffences.filter { !canSentenceBeCalculated(it) }
    if (unsupportedSentences.isNotEmpty()) {
      throw UnsupportedSentenceException(
        "Calculation engine cannot calculate these sentences ${unsupportedSentences.map(SentenceAndOffences::sentenceCalculationType).joinToString()}",
        unsupportedSentences
      )
    }
  }

  private fun canSentenceBeCalculated(sentenceAndOffence: SentenceAndOffences): Boolean {
    return supportedSentences.any { it.sentenceType == sentenceAndOffence.sentenceCalculationType }
  }

  companion object {
    private val supportedSentences: List<SupportedSentence> = listOf(
      SupportedSentence("ADIMP", offenceDateCondition = { it.isAfterOrEqualTo(ImportantDates.CJA_DATE) }),
      SupportedSentence("ADIMP_ORA", offenceDateCondition = { it.isAfterOrEqualTo(ImportantDates.ORA_DATE) }),
      SupportedSentence("YOI", offenceDateCondition = { it.isAfterOrEqualTo(ImportantDates.CJA_DATE) }),
      SupportedSentence("YOI_ORA", offenceDateCondition = { it.isAfterOrEqualTo(ImportantDates.ORA_DATE) }),
      SupportedSentence(
        "SEC91_03",
        sentenceAtCondition = { it.isBefore(ImportantDates.SEC_SENTENCE_DATE) },
        offenceDateCondition = { it.isAfterOrEqualTo(ImportantDates.CJA_DATE) }
      ),
      SupportedSentence(
        "SEC91_03_ORA",
        sentenceAtCondition = { it.isBefore(ImportantDates.SEC_SENTENCE_DATE) },
        offenceDateCondition = { it.isAfterOrEqualTo(ImportantDates.ORA_DATE) }
      ),
      SupportedSentence(
        "SEC250",
        sentenceAtCondition = { it.isAfterOrEqualTo(ImportantDates.SEC_SENTENCE_DATE) }
      ),
      SupportedSentence(
        "SEC250_ORA",
        sentenceAtCondition = { it.isAfterOrEqualTo(ImportantDates.SEC_SENTENCE_DATE) },
        offenceDateCondition = { it.isAfterOrEqualTo(ImportantDates.ORA_DATE) }
      )
    )
  }
}
