package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfileCalculation

@Service
class OffenderSentenceProfileCalculationService(
  val sentenceCalculationService: SentenceCalculationService
) {

  fun identify(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    for (sentence in offenderSentenceProfile.sentences) {
      sentenceCalculationService.identify(sentence, offenderSentenceProfile.offender)
    }
    return offenderSentenceProfile
  }

  fun calculate(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    for (sentence in offenderSentenceProfile.sentences) {
      sentenceCalculationService.calculate(sentence)
    }
    return offenderSentenceProfile
  }

  fun extract(
    offenderSentenceProfile: OffenderSentenceProfile
  ): OffenderSentenceProfileCalculation {
    return when (offenderSentenceProfile.sentences.size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> extractSingle(offenderSentenceProfile)
      else -> {
        extractMultiple(offenderSentenceProfile)
      }
    }
  }

  fun aggregate(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    return when (offenderSentenceProfile.sentences.size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> offenderSentenceProfile
      else -> {
        aggregateMultiple(offenderSentenceProfile)
      }
    }
  }

  private fun aggregateMultiple(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    return offenderSentenceProfile
  }

  private fun extractSingle(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfileCalculation {
    val sentenceCalculation = offenderSentenceProfile.sentences[0].sentenceCalculation
    return OffenderSentenceProfileCalculation(
      sentenceCalculation.licenceExpiryDate,
      sentenceCalculation.expiryDate,
      sentenceCalculation.releaseDate,
      sentenceCalculation.topUpSupervisionDate,
      sentenceCalculation.isReleaseDateConditional
    )
  }

  private fun extractMultiple(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfileCalculation {
    // this needs to do handle the multiplicity of sentences
    val sentenceCalculation = offenderSentenceProfile.sentences[0].sentenceCalculation
    return OffenderSentenceProfileCalculation(
      sentenceCalculation.licenceExpiryDate,
      sentenceCalculation.expiryDate,
      sentenceCalculation.releaseDate,
      sentenceCalculation.topUpSupervisionDate,
      sentenceCalculation.isReleaseDateConditional
    )
  }
}
