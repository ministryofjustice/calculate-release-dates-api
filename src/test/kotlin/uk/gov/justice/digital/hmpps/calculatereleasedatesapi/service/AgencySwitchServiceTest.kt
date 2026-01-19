package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AgencySwitchUpdateResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.AgencySwitch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.AgencySwitchAgency

class AgencySwitchServiceTest {

  private val prisonApiClient = mock<PrisonApiClient>()
  private val agencySwitchService = AgencySwitchService(prisonApiClient)

  private val existingAgency = AgencySwitchAgency("EXISTING", "Existing")
  private val newAgency = AgencySwitchAgency("NEW", "New")
  private val oldAgency = AgencySwitchAgency("OLD", "Old")

  @Test
  fun `should update agency switch for any differences`() {
    val requiredAgencies = setOf("NEW", "EXISTING")

    whenever(prisonApiClient.getAgenciesWithSwitchOn(AgencySwitch.SENTENCE_CALC))
      .thenReturn(
        listOf(existingAgency, oldAgency),
        listOf(newAgency, existingAgency),
      )

    whenever(prisonApiClient.turnSwitchOnForAgency(any(), any())).thenReturn(true)
    whenever(prisonApiClient.turnSwitchOffForAgency(any(), any())).thenReturn(true)

    val result = agencySwitchService.setSwitchForAgencies(AgencySwitch.SENTENCE_CALC, requiredAgencies)
    assertThat(result).isEqualTo(
      AgencySwitchUpdateResult(
        requiredAgencies = requiredAgencies,
        agenciesSwitchedOn = setOf("NEW"),
        agenciesSwitchedOff = setOf("OLD"),
        current = listOf(Agency("NEW", "New"), Agency("EXISTING", "Existing")),
      ),
    )

    verify(prisonApiClient).turnSwitchOnForAgency("NEW", AgencySwitch.SENTENCE_CALC)
    verify(prisonApiClient, never()).turnSwitchOnForAgency("OLD", AgencySwitch.SENTENCE_CALC)
    verify(prisonApiClient, never()).turnSwitchOnForAgency("EXISTING", AgencySwitch.SENTENCE_CALC)

    verify(prisonApiClient).turnSwitchOffForAgency("OLD", AgencySwitch.SENTENCE_CALC)
    verify(prisonApiClient, never()).turnSwitchOffForAgency("NEW", AgencySwitch.SENTENCE_CALC)
    verify(prisonApiClient, never()).turnSwitchOffForAgency("EXISTING", AgencySwitch.SENTENCE_CALC)
  }
}
