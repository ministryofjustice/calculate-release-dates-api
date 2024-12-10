package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.BOTUS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE

@Service
class BotusValidationService(
  private val featureToggles: FeatureToggles,
) {

  internal fun validate(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    if (featureToggles.botusConsecutiveJourney) {
      log.info("BOTUS consecutive and concurrent journeys are feature enabled - therefore the 'unsupported' validation for BOTUS will not run")
      return emptyList()
    }
    log.info("BOTUS consecutive and concurrent journeys are unsupported, running unsupported validation")

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

  companion object {
    private val log = LoggerFactory.getLogger(BotusValidationService::class.java)
  }
}
