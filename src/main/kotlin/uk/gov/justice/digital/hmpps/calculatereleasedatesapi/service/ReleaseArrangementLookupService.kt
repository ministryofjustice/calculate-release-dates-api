package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSPlusCheckResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType

@Service
class ReleaseArrangementLookupService(
  private val sdsEarlyReleaseExclusionMappingService: SDSEarlyReleaseExclusionMappingService,
  private val manageOffencesService: ManageOffencesService,
) {
  fun populateReleaseArrangements(sentencesAndOffences: List<SentenceAndOffence>): List<SentenceAndOffenceWithReleaseArrangements> {
    log.info("Checking ${sentencesAndOffences.size} sentences for SDS release arrangements")

    val sentencesAndOffencesWithSentenceType = sentencesAndOffences.map { it to SentenceCalculationType.from(it.sentenceCalculationType) }
    val sdsOffenceCodes = sentencesAndOffencesWithSentenceType.filter { (_, sentenceType) -> sentenceType.isSDS() }.map { (sentenceAndOffence, _) -> sentenceAndOffence.offence.offenceCode }.distinct().sorted()

    if (sdsOffenceCodes.isEmpty()) {
      return sentencesAndOffences.map { SentenceAndOffenceWithReleaseArrangements(it, releaseArrangements = null) }
    }

    val sdsOffenceDetailsByOffenceCode = manageOffencesService.getSdsOffenceDetailsForOffenceCodes(sdsOffenceCodes).associateBy { it.offenceCode }

    return sentencesAndOffencesWithSentenceType.map { (sentenceAndOffence, sentenceType) ->
      val offenceCode = sentenceAndOffence.offence.offenceCode
      if (sentenceType.isSDS()) {
        val sdsOffenceDetails = requireNotNull(sdsOffenceDetailsByOffenceCode[offenceCode]) { "Missing SDS offence details for $offenceCode" }
        val sdsPlusCheckResult = SDSPlusCheckResult(sentenceAndOffence, sdsOffenceDetails.pcscMarkers)
        SentenceAndOffenceWithReleaseArrangements(
          sentenceAndOffence,
          SDSReleaseArrangements(
            isSDSPlus = sdsPlusCheckResult.isSDSPlus,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = sdsPlusCheckResult.isSDSPlusEligibleSentenceTypeLengthAndOffence,
            sdsEarlyReleaseExclusions =
            sdsEarlyReleaseExclusionMappingService.exclusionForOffence(
              sdsOffenceDetails.earlyReleaseExclusions,
              sentenceAndOffence,
            ),
            isSection250 = sentenceType.isSection250(),
          ),
        )
      } else {
        SentenceAndOffenceWithReleaseArrangements(sentenceAndOffence, releaseArrangements = null)
      }
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
