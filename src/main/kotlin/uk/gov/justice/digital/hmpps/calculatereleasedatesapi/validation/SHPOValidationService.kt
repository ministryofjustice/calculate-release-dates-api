package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SDS_40_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SHPO_OFFENCE_COMMENCEMENT_DATE

@Service
class SHPOValidationService {

  private val shpoOffenceCodes = listOf("SE20005", "SE20006", "SE20012", "SE20012A")

  internal fun validate(booking: Booking): List<ValidationMessage> = booking.getAllExtractableSentences().flatMap {
    when (it) {
      is ConsecutiveSentence -> validateConsecutiveSentence(it)
      is StandardDeterminateSentence -> validateStandardDeterminateSentence(it)
      else -> emptyList()
    }
  }

  private fun validateConsecutiveSentence(consecutiveSentence: ConsecutiveSentence): List<ValidationMessage> {
    if (consecutiveSentence.sentencedAt <= SDS_40_COMMENCEMENT_DATE &&
      !isConsecutiveSdsHistoricSentenceInValid(consecutiveSentence)
    ) {
      return emptyList()
    }

    return consecutiveSentence.orderedSentences
      .filterIsInstance<StandardDeterminateSentence>()
      .filter { isRelevantOffence(it) && it.offence.committedAt < SHPO_OFFENCE_COMMENCEMENT_DATE }
      .map {
        ValidationMessage(
          ValidationCode.SHPO_INVALID_DATE,
          listOf(it.caseSequence.toString(), it.lineSequence.toString()),
        )
      }
  }

  private fun validateStandardDeterminateSentence(sentence: StandardDeterminateSentence): List<ValidationMessage> =
    if (isShpoViolation(sentence)) {
      listOf(
        ValidationMessage(
          ValidationCode.SHPO_INVALID_DATE,
          listOf(sentence.caseSequence.toString(), sentence.lineSequence.toString()),
        ),
      )
    } else {
      emptyList()
    }

  private fun isShpoViolation(sentence: StandardDeterminateSentence): Boolean = isRelevantOffence(sentence) &&
    (isSdsSentenceInValid(sentence) || isSdsHistoricSentenceInValid(sentence))

  private fun isRelevantOffence(sentence: StandardDeterminateSentence): Boolean =
    !sentence.isSDSPlus && shpoOffenceCodes.contains(sentence.offence.offenceCode)

  private fun isConsecutiveSdsHistoricSentenceInValid(consecutiveSentence: ConsecutiveSentence): Boolean =
    consecutiveSentence.sentencedAt.isBefore(SDS_40_COMMENCEMENT_DATE) &&
      consecutiveSentence.sentenceCalculation.releaseDate.isAfter(SDS_40_COMMENCEMENT_DATE)

  private fun isSdsSentenceInValid(sentence: CalculableSentence): Boolean =
    sentence.sentencedAt >= SDS_40_COMMENCEMENT_DATE &&
      sentence.offence.committedAt < SHPO_OFFENCE_COMMENCEMENT_DATE

  private fun isSdsHistoricSentenceInValid(sentence: StandardDeterminateSentence): Boolean =
    sentence.sentencedAt.isBefore(SDS_40_COMMENCEMENT_DATE) &&
      sentence.sentenceCalculation.releaseDate.isAfter(SDS_40_COMMENCEMENT_DATE) &&
      sentence.offence.committedAt < SHPO_OFFENCE_COMMENCEMENT_DATE
}
