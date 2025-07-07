package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.eligibility

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService

@Service
class ErsedEligibilityService(
  private val manageOffencesService: ManageOffencesService,
  private val prisonService: PrisonService,
) {

  data class ErsedEligibility(val isValid: Boolean, val reason: String? = null)

  private val edsCalculationTypes = setOf(
    SentenceCalculationType.EDS18.name,
    SentenceCalculationType.EDS21.name,
    SentenceCalculationType.EDSU18.name,
    SentenceCalculationType.LASPO_AR.name,
    SentenceCalculationType.LASPO_DR.name,
  )

  fun sentenceIsEligible(bookingId: Long): ErsedEligibility {
    val sentenceData = prisonService.getSentencesAndOffences(bookingId)

    if (sentenceData.all(::exemptSentenceType)) {
      return ErsedEligibility(false, "No valid ersed sentence types")
    }

    val edsOffenceCodes = getToreraEdsOffenceCodes(sentenceData)
    val part1Codes = manageOffencesService.getToreraCodesByParts().parts[1].orEmpty()

    val hasPart1EdsOffence = part1Codes.any { it in edsOffenceCodes }

    return if (hasPart1EdsOffence) {
      ErsedEligibility(false, "EDS sentence with 19ZA part 1 offence")
    } else {
      ErsedEligibility(true)
    }
  }

  private fun getToreraEdsOffenceCodes(sourceData: List<SentenceAndOffenceWithReleaseArrangements>): List<String> = sourceData
    .asSequence()
    .filter { it.sentenceCalculationType in edsCalculationTypes }
    .map { it.offence.offenceCode }
    .toList()

  private fun exemptSentenceType(sentenceAndOffence: SentenceAndOffenceWithReleaseArrangements): Boolean {
    val type = runCatching {
      SentenceCalculationType.valueOf(sentenceAndOffence.sentenceCalculationType)
    }.getOrElse { return true }

    return type.sentenceType == SentenceType.AFine ||
      type in listOf(SentenceCalculationType.DTO, SentenceCalculationType.DTO_ORA) ||
      type.recallType !== null
  }
}
