package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode

@Service
class ManageOffencesService(
  private val manageOffencesApiClient: ManageOffencesApiClient,
) {
  fun getPcscMarkersForOffenceCodes(vararg offenceCodes: String): List<OffencePcscMarkers> {
    return manageOffencesApiClient.getPCSCMarkersForOffences(offenceCodes.toList())
  }

  fun getSdsExclusionsForOffenceCodes(offenceCodes: List<String>): List<SDSEarlyReleaseExclusionForOffenceCode> {
    return manageOffencesApiClient.getSdsExclusionsForOffenceCodes(offenceCodes)
  }

  fun getOffences(offenceCodes: List<String>): List<Offence> {
    return manageOffencesApiClient.getOffences(offenceCodes.distinct())
  }
}
