package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSPlusCheckResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence

@Service
class SDSPlusReleaseArrangementLookupService(
  private val manageOffencesService: ManageOffencesService,
) {
  fun checkForSDSPlus(sentencesAndOffences: List<SentenceAndOffence>): List<SDSPlusCheckResult> {
    val offences = getOffencesByOffenceCode(sentencesAndOffences)

    val sdsPlusMarkersByOffences =
      if (offences.isNotEmpty()) {
        manageOffencesService.getPcscMarkersForOffenceCodes(offences)
          .associateBy { it.offenceCode }
      } else {
        emptyMap()
      }

    return sentencesAndOffences.map { sentenceAndOffence ->
      SDSPlusCheckResult(sentenceAndOffence, sdsPlusMarkersByOffences)
    }
  }

  private fun getOffencesByOffenceCode(sentencesAndOffences: List<SentenceAndOffence>): List<String> {
    return sentencesAndOffences.map { it.offence.offenceCode }.sorted()
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
