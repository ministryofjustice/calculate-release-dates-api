package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CantExtractMultipleSentencesException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfileCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType
import java.time.LocalDate

@Service
class OffenderSentenceProfileCalculationService(
  val sentenceCalculationService: SentenceCalculationService,
  val extractionService: SentencesExtractionService
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
    val sentence = offenderSentenceProfile.sentences[0]
    val sentenceCalculation = sentence.sentenceCalculation
    if (sentence.sentenceTypes.contains(SentenceType.SLED)) {
      return OffenderSentenceProfileCalculation(
        null,
        sentenceCalculation.expiryDate,
        sentenceCalculation.releaseDate,
        sentenceCalculation.topUpSupervisionDate,
        sentenceCalculation.isReleaseDateConditional
      )
    } else {
      return OffenderSentenceProfileCalculation(
        sentenceCalculation.licenceExpiryDate,
        sentenceCalculation.expiryDate,
        sentenceCalculation.releaseDate,
        sentenceCalculation.topUpSupervisionDate,
        sentenceCalculation.isReleaseDateConditional
      )
    }
  }

  private fun extractMultiple(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfileCalculation {
    if (
      extractionService.hasNoConcurrentSentences(offenderSentenceProfile.sentences.stream()) &&
      extractionService.allOverlap(offenderSentenceProfile.sentences)
    ) {

      val latestReleaseDate: LocalDate? = extractionService.mostRecent(
        offenderSentenceProfile.sentences, SentenceCalculation::releaseDate
      )
      val latestExpiryDate: LocalDate? = extractionService.mostRecent(
        offenderSentenceProfile.sentences, SentenceCalculation::expiryDate
      )
      var latestLicenseExpiryDate: LocalDate? = extractionService.mostRecent(
        offenderSentenceProfile.sentences, SentenceCalculation::licenceExpiryDate
      )

      if (latestLicenseExpiryDate != null &&
        (
          latestLicenseExpiryDate.isEqual(latestReleaseDate) ||
            latestLicenseExpiryDate.isEqual(latestExpiryDate)
          )
      ) {
        latestLicenseExpiryDate = null
      }

      return OffenderSentenceProfileCalculation(
        latestLicenseExpiryDate,
        latestExpiryDate,
        latestReleaseDate,
        extractionService.mostRecent(offenderSentenceProfile.sentences, SentenceCalculation::topUpSupervisionDate),
        offenderSentenceProfile.sentences[0].sentenceCalculation.isReleaseDateConditional
      )
    } else {
      throw CantExtractMultipleSentencesException("Can't extract a single date from multiple sentences")
    }
  }
}
