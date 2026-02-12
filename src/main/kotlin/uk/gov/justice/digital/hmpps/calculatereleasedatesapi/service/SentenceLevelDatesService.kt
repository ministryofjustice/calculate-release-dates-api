package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSentenceOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceLevelDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceRepository

@Component
class SentenceLevelDatesService(
  private val calculationRequestSentenceRepository: CalculationRequestSentenceRepository,
  private val calculationRequestSentenceOutcomeRepository: CalculationRequestSentenceOutcomeRepository,
  private val objectMapper: ObjectMapper,
) {

  fun extractSentenceLevelDates(calculatedReleaseDates: CalculationOutput): List<SentenceLevelDates> {
    val finalReleaseDateUUIDs = calculatedReleaseDates.calculationResult.sentencesImpactingFinalReleaseDate.flatMap { it.sentenceParts().map { it.identifier } }
    return calculatedReleaseDates.sentences.flatMapIndexed { index, sentence ->
      val dates = sentence.releaseDateTypes.getReleaseDateTypes()
        .mapNotNull { releaseDateType -> sentence.sentenceCalculation.getDateByType(releaseDateType)?.let { releaseDateType to it } }
        .toMap()
      sentence.sentenceParts().map { sentencePart ->
        SentenceLevelDates(
          sentence = sentencePart,
          groupIndex = index,
          impactsFinalReleaseDate = sentencePart.identifier in finalReleaseDateUUIDs,
          releaseMultiplier = sentence.sentenceCalculation.unadjustedReleaseDate.multiplier(sentence),
          dates = dates,
        )
      }
    }
  }

  @Transactional
  fun storeSentenceLevelDates(sentenceLevelDates: List<SentenceLevelDates>, sourceData: CalculationSourceData, calculationRequest: CalculationRequest) {
    val sourceSentencesByUUID = sourceData.sentenceAndOffences.associateBy { sentence -> generateUUIDForSentence(sentence.bookingId, sentence.sentenceSequence) }
    sentenceLevelDates
      .mapNotNull { sentenceLevelDates -> sourceSentencesByUUID[sentenceLevelDates.sentence.identifier]?.let { sentenceLevelDates to it } }
      .forEach { (sentenceLevelDates, sourceSentence) ->
        val sentenceAdjustmentsJson =
          sourceData.bookingAndSentenceAdjustments.fold(
            { prisonApiAdjustments -> objectToJson(prisonApiAdjustments.sentenceAdjustments.filter { adjustment -> sourceSentence.sentenceSequence == adjustment.sentenceSequence }, objectMapper) },
            { adjustmentsApiAdjustments -> objectToJson(adjustmentsApiAdjustments.filter { adjustment -> sourceSentence.sentenceSequence == adjustment.sentenceSequence }, objectMapper) },
          )

        val calculationRequestSentence = calculationRequestSentenceRepository.saveAndFlush(
          CalculationRequestSentence(
            calculationRequestId = calculationRequest.id(),
            inputSentenceData = objectToJson(sourceSentence, objectMapper),
            sentenceAdjustments = sentenceAdjustmentsJson,
            impactsFinalReleaseDate = sentenceLevelDates.impactsFinalReleaseDate,
            releaseMultiplier = sentenceLevelDates.releaseMultiplier,
          ),
        )
        sentenceLevelDates.dates.forEach { (type, date) ->
          calculationRequestSentenceOutcomeRepository.save(
            CalculationRequestSentenceOutcome(
              calculationRequestSentenceId = calculationRequestSentence.id!!,
              calculationDateType = type,
              outcomeDate = date,
            ),
          )
        }
      }
  }
}
