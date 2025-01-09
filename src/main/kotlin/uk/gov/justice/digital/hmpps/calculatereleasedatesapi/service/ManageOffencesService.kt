package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode

@Service
class ManageOffencesService(
  private val manageOffencesApiClient: ManageOffencesApiClient,
) {

  fun getPcscMarkersForOffenceCodes(offenceCodes: List<String>): List<OffencePcscMarkers> {
    return manageOffencesApiClient.getPCSCMarkersForOffences(offenceCodes)
  }

  fun getSdsExclusionsForOffenceCodes(offenceCodes: List<String>): List<SDSEarlyReleaseExclusionForOffenceCode> {
    return manageOffencesApiClient.getSdsExclusionsForOffenceCodes(offenceCodes)
  }

  fun getToreraOffenceCodes(): List<String> {
    return manageOffencesApiClient.getToreraOffenceCodes()
  }
}
