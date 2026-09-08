package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence

@Service
class ValidationUtilities {
  fun sortByCaseNumberAndLineSequence(a: SentenceAndOffence, b: SentenceAndOffence): Int {
    if (a.caseSequence > b.caseSequence) return 1
    if (a.caseSequence < b.caseSequence) return -1
    return a.lineSequence - b.lineSequence
  }

  fun buildOverlappingMessageArguments(range1: LocalDateRange, range2: LocalDateRange): List<String> = listOf(
    range1.start.toString(),
    range1.end.toString(),
    range2.start.toString(),
    range2.end.toString(),
  )

  fun createValidationMessage(code: ValidationCode, sentenceAndOffence: SentenceAndOffence, vararg extraArguments: String): ValidationMessage = ValidationMessage(
    code,
    getCaseSeqAndLineSeq(sentenceAndOffence).plus(extraArguments),
    sentenceIdentifier = SentenceIdentifier(sentenceAndOffence.bookingId, sentenceAndOffence.sentenceSequence),
  )

  private fun getCaseSeqAndLineSeq(sentencesAndOffence: SentenceAndOffence): List<String> = listOf(sentencesAndOffence.caseSequence.toString(), sentencesAndOffence.lineSequence.toString())
}
