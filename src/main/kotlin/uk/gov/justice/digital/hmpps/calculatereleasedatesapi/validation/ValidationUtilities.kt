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
  internal fun getCaseSeqAndLineSeq(sentencesAndOffence: SentenceAndOffence): List<String> {
    return listOf(sentencesAndOffence.caseSequence.toString(), sentencesAndOffence.lineSequence.toString())
  }

   /*
    * Inverse of getCaseSeqAndLineSeq, keep both in sync
   */
  internal fun findSentenceAndOffence(caseSeqAndLineSeq: List<String>, sentenceAndOffences: List<SentenceAndOffence>): SentenceAndOffence? {
    val caseSequence = caseSeqAndLineSeq.getOrNull(0)?.toIntOrNull()
    val lineSequence = caseSeqAndLineSeq.getOrNull(1)?.toIntOrNull()
    return sentenceAndOffences.find { it.caseSequence == caseSequence && it.lineSequence == lineSequence }
  }

  fun buildOverlappingMessageArguments(range1: LocalDateRange, range2: LocalDateRange): List<String> = listOf(
    range1.start.toString(),
    range1.end.toString(),
    range2.start.toString(),
    range2.end.toString(),
  )
}
