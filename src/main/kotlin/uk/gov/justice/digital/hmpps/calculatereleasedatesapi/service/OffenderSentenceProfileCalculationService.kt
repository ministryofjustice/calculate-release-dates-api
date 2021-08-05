package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
  val extractionService: SentencesExtractionService,
  val combinationService: SentenceCombinationService
) {

  fun identify(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    for (sentence in offenderSentenceProfile.sentences) {
      sentenceCalculationService.identify(sentence, offenderSentenceProfile.offender)
    }
    return offenderSentenceProfile
  }

  fun associateConsecutive(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    for (sentence in offenderSentenceProfile.sentences) {
      sentence.associateSentences(offenderSentenceProfile.sentences)
    }
    return offenderSentenceProfile
  }

  fun calculate(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    for (sentence in offenderSentenceProfile.sentences) {
      sentenceCalculationService.calculate(sentence)
      log.info(sentence.buildString())
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

  fun combine(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfile {
    return when (offenderSentenceProfile.sentences.size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> offenderSentenceProfile
      else -> {
        val workingOffenderSentenceProfile = combinationService.combineConsecutiveSentences(offenderSentenceProfile)
        for (sentence in workingOffenderSentenceProfile.sentences) {
          if (!sentence.isSentenceCalculated()) {
            sentenceCalculationService.calculate(sentence)
            log.info(sentence.buildString())
          }
        }
        return workingOffenderSentenceProfile
      }
    }
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

      var isReleaseDateConditional = extractionService.getAssociatedReleaseType(
        offenderSentenceProfile.sentences, latestReleaseDate
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
      } else {
        // PSI Example 16 Release is therefore on license which means the release date is a CRD
        isReleaseDateConditional = true
      }

      return OffenderSentenceProfileCalculation(
        latestLicenseExpiryDate,
        latestExpiryDate,
        latestReleaseDate,
        extractionService.mostRecent(offenderSentenceProfile.sentences, SentenceCalculation::topUpSupervisionDate),
        isReleaseDateConditional
      )
    } else {
      throw CantExtractMultipleSentencesException("Can't extract a single date from multiple sentences")
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
