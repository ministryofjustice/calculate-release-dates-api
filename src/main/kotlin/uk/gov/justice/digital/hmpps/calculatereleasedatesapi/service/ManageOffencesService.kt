package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffenceSdsExclusion

@Service
class ManageOffencesService(
  private val manageOffencesApiClient: ManageOffencesApiClient,
) {

  fun getPcscMarkersForOffenceCodes(offenceCodes: List<String>): List<OffencePcscMarkers> = manageOffencesApiClient.getPCSCMarkersForOffences(offenceCodes)

  fun getSdsExclusionsForOffenceCodes(offenceCodes: List<String>): List<OffenceSdsExclusion> = manageOffencesApiClient.getSdsExclusionsForOffenceCodes(offenceCodes)

  fun getToreraOffenceCodes(): List<String> = manageOffencesApiClient.getToreraOffenceCodes()
}
