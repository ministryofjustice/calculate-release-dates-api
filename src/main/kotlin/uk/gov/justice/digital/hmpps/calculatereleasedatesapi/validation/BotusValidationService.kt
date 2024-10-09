package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.BOTUS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE

@Service
class BotusValidationService {

  internal fun validateBotusWithOtherSentence(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val botusSentences =
      sourceData.sentenceAndOffences.filter { SentenceCalculationType.from(it.sentenceCalculationType) == BOTUS }
    if (botusSentences.isNotEmpty() && botusSentences.size != sourceData.sentenceAndOffences.size) {
      validationMessages.add(ValidationMessage(code = BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE))
    }
    return validationMessages
  }
}
