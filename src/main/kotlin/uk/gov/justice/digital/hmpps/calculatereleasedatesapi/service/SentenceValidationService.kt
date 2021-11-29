package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.ADIMP
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.ADIMP_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.SEC250
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.SEC250_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.SEC91_03
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.SEC91_03_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.YOI
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.YOI_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SupportedSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.CJA_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.ORA_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SEC_SENTENCE_DATE
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
    return supportedSentences.any { it.sentenceType.name == sentenceAndOffence.sentenceCalculationType }
  }

  companion object {
    /*
      These supported sentences include conditions for the offenceDate and sentenceAtDate. They're currently not used
      but the validation for this will come.
     */
    private val supportedSentences: List<SupportedSentence> = listOf(
      SupportedSentence(ADIMP, offenceDateCondition = { it.isAfterOrEqualTo(CJA_DATE) }),
      SupportedSentence(ADIMP_ORA, offenceDateCondition = { it.isAfterOrEqualTo(ORA_DATE) }),
      SupportedSentence(YOI, offenceDateCondition = { it.isAfterOrEqualTo(CJA_DATE) }),
      SupportedSentence(YOI_ORA, offenceDateCondition = { it.isAfterOrEqualTo(ORA_DATE) }),
      SupportedSentence(
        SEC91_03,
        sentenceAtCondition = { it.isBefore(SEC_SENTENCE_DATE) },
        offenceDateCondition = { it.isAfterOrEqualTo(CJA_DATE) }
      ),
      SupportedSentence(
        SEC91_03_ORA,
        sentenceAtCondition = { it.isBefore(SEC_SENTENCE_DATE) },
        offenceDateCondition = { it.isAfterOrEqualTo(ORA_DATE) }
      ),
      SupportedSentence(
        SEC250,
        sentenceAtCondition = { it.isAfterOrEqualTo(SEC_SENTENCE_DATE) }
      ),
      SupportedSentence(
        SEC250_ORA,
        sentenceAtCondition = { it.isAfterOrEqualTo(SEC_SENTENCE_DATE) },
        offenceDateCondition = { it.isAfterOrEqualTo(ORA_DATE) }
      )
    )
  }
}
