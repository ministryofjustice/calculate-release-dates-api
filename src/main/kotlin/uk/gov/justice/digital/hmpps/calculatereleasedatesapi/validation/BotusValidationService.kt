package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.BOTUS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE

@Service
class BotusValidationService {

  internal fun validate(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val botusSentences = getBotusSentences(sourceData)

    if (botusSentences.isEmpty() || botusSentences.size == sourceData.sentenceAndOffences.size) {
      return emptyList()
    }

    return listOf(ValidationMessage(code = BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE))
  }

  private fun getBotusSentences(sourceData: PrisonApiSourceData): List<SentenceAndOffence> {
    return sourceData.sentenceAndOffences.filter {
      SentenceCalculationType.from(it.sentenceCalculationType) == BOTUS
    }
  }
}
