package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentencesExtractionService

@Service
class ExtinguishedSentenceGroupChecker(
  private val extractionService: SentencesExtractionService,
  private val featureToggles: FeatureToggles,
  private val sdsLegislationConfiguration: SDSLegislationConfiguration,
) {

  fun mode(calculationOutput: CalculationOutput, booking: Booking): ExtinguishedSentenceValidationMode {
    if (!featureToggles.routePreProgressionExtinguishedSentenceToManual || sdsLegislationConfiguration.progressionModelLegislation == null || wasNotAssignedAProgressionModelTranche(calculationOutput)) {
      return ExtinguishedSentenceValidationMode.INVALID
    }
    val commencementDate = sdsLegislationConfiguration.progressionModelLegislation.commencementDate()
    val relevantAdjustments = listOf(
      booking.adjustments.getOrEmptyList(AdjustmentType.REMAND),
      booking.adjustments.getOrEmptyList(AdjustmentType.TAGGED_BAIL),
      booking.adjustments.getOrEmptyList(AdjustmentType.RECALL_REMAND),
      booking.adjustments.getOrEmptyList(AdjustmentType.RECALL_TAGGED_BAIL),
    ).flatten()
    return if (relevantAdjustments.any { it.appliesToSentencesFrom >= commencementDate }) {
      ExtinguishedSentenceValidationMode.INVALID
    } else {
      ExtinguishedSentenceValidationMode.ROUTE_TO_MANUAL
    }
  }

  fun check(sentenceGroup: SentenceGroup): ExtinguishedSentenceGroupCheckResult {
    val determinateSentences = sentenceGroup.sentences.filter { !it.isRecall() }
    if (determinateSentences.isNotEmpty()) {
      val earliestSentenceDate = determinateSentences.minOf { it.sentencedAt }
      val latestReleaseDateSentence = extractionService.mostRecentSentence(
        determinateSentences,
        SentenceCalculation::adjustedUncappedDeterminateReleaseDate,
      )
      val uncappedCrd = latestReleaseDateSentence.sentenceCalculation.adjustedUncappedDeterminateReleaseDate
      if (earliestSentenceDate.minusDays(1).isAfter(uncappedCrd)) {
        val hasRemand = latestReleaseDateSentence.sentenceCalculation.adjustments.remand != 0L
        val hasTaggedBail = latestReleaseDateSentence.sentenceCalculation.adjustments.taggedBail != 0L
        return ExtinguishedSentenceGroupCheckResult(isExtinguished = hasRemand || hasTaggedBail, hasRemand = hasRemand, hasTaggedBail = hasTaggedBail)
      }
    }
    return ExtinguishedSentenceGroupCheckResult(isExtinguished = false, hasRemand = false, hasTaggedBail = false)
  }

  private fun wasNotAssignedAProgressionModelTranche(calculationOutput: CalculationOutput): Boolean = calculationOutput.calculationResult.trancheAllocationByLegislationName[LegislationName.SDS_PROGRESSION_MODEL] == null

  data class ExtinguishedSentenceGroupCheckResult(val isExtinguished: Boolean, val hasRemand: Boolean, val hasTaggedBail: Boolean)

  enum class ExtinguishedSentenceValidationMode { ROUTE_TO_MANUAL, INVALID }
}
