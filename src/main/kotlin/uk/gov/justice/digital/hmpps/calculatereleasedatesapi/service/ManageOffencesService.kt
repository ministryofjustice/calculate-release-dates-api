package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers

@Service
class ManageOffencesService(
  private val manageOffencesApiClient: ManageOffencesApiClient,
) {
  fun getPcscMarkersForOffenceCodes(vararg offenceCodes: String): List<OffencePcscMarkers> {
    return manageOffencesApiClient.getPCSCMarkersForOffences(offenceCodes.toList())
  }
}
