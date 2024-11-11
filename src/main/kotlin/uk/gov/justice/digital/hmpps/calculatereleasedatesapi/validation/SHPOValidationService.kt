package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SDS_40_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SHPO_OFFENCE_COMMENCEMENT_DATE

@Service
class SHPOValidationService {

  private val shpoOffenceCodes = listOf("SE20005", "SE20006", "SE20012", "SE20012A")

  internal fun validate(booking: Booking): List<ValidationMessage> = booking.getAllExtractableSentences()
    .flatMap { if (it is ConsecutiveSentence) it.orderedSentences else listOf(it) }
    .filterIsInstance<StandardDeterminateSentence>()
    .filter { isShpoViolation(it) }
    .map {
      ValidationMessage(
        ValidationCode.SHPO_INVALID_DATE,
        listOf(it.caseSequence.toString(), it.lineSequence.toString()),
      )
    }

  private fun isShpoViolation(sentence: StandardDeterminateSentence): Boolean {
    if (!isRelevantOffence(sentence)) return false
    if (isSdsSentenceInValid(sentence)) return true
    if (isSdsHistoricSentenceInValid(sentence)) return true
    return false
  }

  private fun isRelevantOffence(sentence: StandardDeterminateSentence): Boolean =
    !sentence.isSDSPlus && shpoOffenceCodes.contains(sentence.offence.offenceCode)

  private fun isSdsSentenceInValid(sentence: StandardDeterminateSentence): Boolean =
    sentence.sentencedAt >= SDS_40_COMMENCEMENT_DATE &&
      sentence.offence.committedAt < SHPO_OFFENCE_COMMENCEMENT_DATE

  private fun isSdsHistoricSentenceInValid(sentence: StandardDeterminateSentence): Boolean =
    sentence.sentencedAt.isBefore(SDS_40_COMMENCEMENT_DATE) &&
      sentence.sentenceCalculation.releaseDate.isAfter(SDS_40_COMMENCEMENT_DATE) &&
      sentence.offence.committedAt < SHPO_OFFENCE_COMMENCEMENT_DATE
}
