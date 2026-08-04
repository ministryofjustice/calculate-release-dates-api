package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.SdsOffenceDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.apiclients.ManageOffencesApiClient

@Service
class ManageOffencesService(
  private val manageOffencesApiClient: ManageOffencesApiClient,
) {
  fun getSdsOffenceDetailsForOffenceCodes(offenceCodes: List<String>): List<SdsOffenceDetails> = manageOffencesApiClient.getSdsOffenceDetails(offenceCodes)

  fun getToreraOffenceCodes(): List<String> = manageOffencesApiClient.getToreraOffenceCodes()
}
