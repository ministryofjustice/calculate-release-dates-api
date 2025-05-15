package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class UnsupportedValidationService {

  internal fun validateUnsupportedSuspendedOffences(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val unSupportedEncouragingOffenceCodes = findUnsupportedSuspendedOffenceCodes(sentencesAndOffence)
    if (unSupportedEncouragingOffenceCodes.isNotEmpty()) {
      return listOf(ValidationMessage(ValidationCode.UNSUPPORTED_SUSPENDED_OFFENCE))
    }
    return emptyList()
  }

  internal fun validateUnsupportedEncouragingOffences(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val unSupportedEncouragingOffenceCodes = findUnsupportedEncouragingOffenceCodes(sentencesAndOffence)
    if (unSupportedEncouragingOffenceCodes.isNotEmpty()) {
      return listOf(ValidationMessage(ValidationCode.UNSUPPORTED_OFFENCE_ENCOURAGING_OR_ASSISTING))
    }
    return emptyList()
  }

  internal fun validateUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val unSupportedEncouragingOffenceCodes = findUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence)
    if (unSupportedEncouragingOffenceCodes.isNotEmpty()) {
      return listOf(ValidationMessage(ValidationCode.UNSUPPORTED_BREACH_97))
    }
    return emptyList()
  }

  private fun findUnsupportedEncouragingOffenceCodes(sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> {
    val offenceCodesToFilter = (2..13).map { "SC070${"%02d".format(it)}" }
    return sentenceAndOffences.filter { it.offence.offenceCode in offenceCodesToFilter }
  }

  private fun findUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> = sentencesAndOffence.filter {
    it.offence.offenceCode.startsWith("PH97003") &&
      it.offence.offenceStartDate != null &&
      it.offence.offenceStartDate.isAfterOrEqualTo(AFTER_97_BREACH_PROVISION_INVALID)
  }

  private fun findUnsupportedSuspendedOffenceCodes(sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> {
    val offenceCodesToFilter = listOf("SE20512", "CJ03523")
    return sentenceAndOffences.filter { it.offence.offenceCode in offenceCodesToFilter }
  }

  companion object {
    private val AFTER_97_BREACH_PROVISION_INVALID = LocalDate.of(2020, 12, 1)
  }
}
