package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.ToreraSchedulePartCodes

@Service
class ManageOffencesService(
  private val manageOffencesApiClient: ManageOffencesApiClient,
) {

  fun getPcscMarkersForOffenceCodes(offenceCodes: List<String>): List<OffencePcscMarkers> = manageOffencesApiClient.getPCSCMarkersForOffences(offenceCodes)

  fun getSdsExclusionsForOffenceCodes(offenceCodes: List<String>): List<SDSEarlyReleaseExclusionForOffenceCode> = manageOffencesApiClient.getSdsExclusionsForOffenceCodes(offenceCodes)

  fun getToreraOffenceCodes(): List<String> = manageOffencesApiClient.getToreraOffenceCodes()

  fun getToreraCodesByParts(): ToreraSchedulePartCodes = manageOffencesApiClient.getToreraCodesByParts()
}
