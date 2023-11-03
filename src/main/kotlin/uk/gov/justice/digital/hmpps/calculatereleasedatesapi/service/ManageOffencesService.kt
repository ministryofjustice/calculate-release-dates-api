package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData

@Service
class ManageOffencesService(
  private val manageOffencesApiClient: ManageOffencesApiClient,
) {
  fun getPcscMarkersForOffenceCodes(vararg offenceCodes: String): List<OffencePcscMarkers> {
    return manageOffencesApiClient.getPCSCMarkersForOffences(offenceCodes.toList())
  }

  fun doesOffenceCodeHavePcscMarkers(offenceCode: String): Boolean {
    val returnedPcscMarkers = getPcscMarkersForOffenceCodes(offenceCode).firstOrNull();
    return returnedPcscMarkers!!.pcscMarkers.inListA || returnedPcscMarkers.pcscMarkers.inListB || returnedPcscMarkers.pcscMarkers.inListC || returnedPcscMarkers.pcscMarkers.inListD
  }
}