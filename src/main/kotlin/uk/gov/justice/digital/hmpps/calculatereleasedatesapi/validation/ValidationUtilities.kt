package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence

@Service
class ValidationUtilities {
  fun sortByCaseNumberAndLineSequence(a: SentenceAndOffence, b: SentenceAndOffence): Int {
    if (a.caseSequence > b.caseSequence) return 1
    if (a.caseSequence < b.caseSequence) return -1
    return a.lineSequence - b.lineSequence
  }
  internal fun getCaseSeqAndLineSeq(sentencesAndOffence: SentenceAndOffence) = listOf(sentencesAndOffence.caseSequence.toString(), sentencesAndOffence.lineSequence.toString())
}
