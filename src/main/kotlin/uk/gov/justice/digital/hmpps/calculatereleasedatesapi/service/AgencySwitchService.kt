package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AgencySwitchUpdateResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.AgencySwitch

@Component
class AgencySwitchService(private val prisonApiClient: PrisonApiClient) {

  private val log = LoggerFactory.getLogger(this::class.java)

  fun getAgenciesWithSwitchOn(agencySwitch: AgencySwitch): List<Agency> = prisonApiClient.getAgenciesWithSwitchOn(agencySwitch)
    .map { Agency(agencyId = it.agencyId, description = it.name) }

  fun setSwitchForAgencies(agencySwitch: AgencySwitch, requiredAgencyIds: Set<String>): AgencySwitchUpdateResult {
    val agenciesWithSwitchOn = getAgenciesWithSwitchOn(agencySwitch)
    val agenciesToSwitchOn = requiredAgencyIds - agenciesWithSwitchOn.map { it.agencyId }.toSet()
    val agenciesToSwitchOff = agenciesWithSwitchOn.map { it.agencyId }.toSet() - requiredAgencyIds

    agenciesToSwitchOn.forEach { agency -> switchOnQuietly(agency, agencySwitch) }
    agenciesToSwitchOff.forEach { agency -> switchOffQuietly(agency, agencySwitch) }

    return AgencySwitchUpdateResult(
      requiredAgencies = requiredAgencyIds,
      agenciesSwitchedOn = agenciesToSwitchOn,
      agenciesSwitchedOff = agenciesToSwitchOff,
      current = getAgenciesWithSwitchOn(agencySwitch),
    )
  }

  private fun switchOnQuietly(agencyId: String, agencySwitch: AgencySwitch) {
    val result = prisonApiClient.turnSwitchOnForAgency(agencyId, agencySwitch)
    if (!result) {
      log.error("Failed to switch on $agencySwitch for $agencyId")
    }
  }

  private fun switchOffQuietly(agencyId: String, agencySwitch: AgencySwitch) {
    val result = prisonApiClient.turnSwitchOffForAgency(agencyId, agencySwitch)
    if (!result) {
      log.error("Failed to switch off $agencySwitch for $agencyId")
    }
  }
}
