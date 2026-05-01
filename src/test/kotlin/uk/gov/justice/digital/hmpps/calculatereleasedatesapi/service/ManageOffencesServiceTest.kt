package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffenceSdsExclusionIndicator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.SdsOffenceDetails

class ManageOffencesServiceTest {

  private val mockManageOffencesApiClient = mock<ManageOffencesApiClient>()
  private val underTest = ManageOffencesService(mockManageOffencesApiClient)

  @Test
  fun getSdsOffenceDetailsForOffenceCodes() {
    val pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false)
    val moResult = listOf(
      SdsOffenceDetails("SX01", pcscMarkers, listOf(OffenceSdsExclusionIndicator.SEXUAL)),
      SdsOffenceDetails("V01", pcscMarkers, listOf(OffenceSdsExclusionIndicator.VIOLENT)),
      SdsOffenceDetails("N01", pcscMarkers, listOf(OffenceSdsExclusionIndicator.NONE)),
    )
    whenever(mockManageOffencesApiClient.getSdsOffenceDetails(listOf("SX01", "V01", "N01"))).thenReturn(moResult)
    val testResult = underTest.getSdsOffenceDetailsForOffenceCodes(listOf("SX01", "V01", "N01"))
    verify(mockManageOffencesApiClient, times(1)).getSdsOffenceDetails(listOf("SX01", "V01", "N01"))
    assertThat(testResult).isEqualTo(moResult)
  }
}
