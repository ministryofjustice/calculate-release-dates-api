package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSPlusCheckResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode

@Service
class ReleaseArrangementLookupService(
  private val sdsPlusReleaseArrangementLookupService: SDSPlusReleaseArrangementLookupService,
  private val sdsReleaseArrangementLookupService: SDSReleaseArrangementLookupService,
  private val manageOffencesService: ManageOffencesService,
) {
  fun populateReleaseArrangements(sentencesAndOffences: List<SentenceAndOffence>): List<SentenceAndOffenceWithReleaseArrangements> {
    log.info("Checking ${sentencesAndOffences.size} sentences for SDS release arrangements")

    val checkedForSDSPlus = checkForSDSPlus(sentencesAndOffences)

    val sdsExclusionsForOffences = fetchSdsExclusions(excludeIfSDSPlus = checkedForSDSPlus)

    return checkedForSDSPlus.map { sdsPlusCheckResult ->
      SentenceAndOffenceWithReleaseArrangements(
        sdsPlusCheckResult,
        sdsReleaseArrangementLookupService.exclusionForOffence(
          sdsExclusionsForOffences,
          sdsPlusCheckResult.sentenceAndOffence,
          sdsPlusCheckResult.isSDSPlus,
        ),
      )
    }
  }

  private fun checkForSDSPlus(sentencesAndOffences: List<SentenceAndOffence>): List<SDSPlusCheckResult> {
    return sdsPlusReleaseArrangementLookupService.checkForSDSPlus(sentencesAndOffences)
  }

  private fun fetchSdsExclusions(excludeIfSDSPlus: List<SDSPlusCheckResult>): Map<String, SDSEarlyReleaseExclusionForOffenceCode> {
    val offenceCodesExcludingSDSPlus = sdsReleaseArrangementLookupService.offenceCodesExcludingSDSPlus(excludeIfSDSPlus)
    if (offenceCodesExcludingSDSPlus.isNotEmpty()) {
      return manageOffencesService
        .getSdsExclusionsForOffenceCodes(offenceCodesExcludingSDSPlus)
        .associateBy { it.offenceCode }
    }
    return emptyMap()
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
