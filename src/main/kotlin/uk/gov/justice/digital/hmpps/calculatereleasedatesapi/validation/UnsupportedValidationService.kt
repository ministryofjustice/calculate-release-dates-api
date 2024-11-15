package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SENTENCING_ACT_2020_COMMENCEMENT
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

  internal fun validateSe20Offences(data: PrisonApiSourceData): List<ValidationMessage> {
    val invalidOffences = data.sentenceAndOffences.filter {
      it.offence.offenceCode.startsWith("SE20") &&
        it.offence.offenceStartDate?.isBefore(SENTENCING_ACT_2020_COMMENCEMENT) ?: false
    }

    return if (invalidOffences.size == 1) {
      listOf(
        ValidationMessage(
          ValidationCode.SE2020_INVALID_OFFENCE_DETAIL,
          listOf(invalidOffences.first().offence.offenceCode),
        ),
      )
    } else {
      invalidOffences.map {
        ValidationMessage(
          ValidationCode.SE2020_INVALID_OFFENCE_COURT_DETAIL,
          listOf(it.caseSequence.toString(), it.lineSequence.toString()),
        )
      }
    }
  }

  private fun findUnsupportedEncouragingOffenceCodes(sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> {
    val offenceCodesToFilter = (2..13).map { "SC070${"%02d".format(it)}" }
    return sentenceAndOffences.filter { it.offence.offenceCode in offenceCodesToFilter }
  }

  private fun findUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> {
    return sentencesAndOffence.filter {
      it.offence.offenceCode.startsWith("PH97003") && it.offence.offenceStartDate != null &&
        it.offence.offenceStartDate.isAfterOrEqualTo(AFTER_97_BREACH_PROVISION_INVALID)
    }
  }

  private fun findUnsupportedSuspendedOffenceCodes(sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): List<SentenceAndOffence> {
    val offenceCodesToFilter = listOf("SE20512", "CJ03523")
    return sentenceAndOffences.filter { it.offence.offenceCode in offenceCodesToFilter }
  }

  companion object {
    private val AFTER_97_BREACH_PROVISION_INVALID = LocalDate.of(2020, 12, 1)
  }
}
