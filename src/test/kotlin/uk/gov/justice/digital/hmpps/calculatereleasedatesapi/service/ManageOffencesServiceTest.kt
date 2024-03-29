package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PcscMarkers

class ManageOffencesServiceTest {

  private val mockManageOffencesApiClient = mock<ManageOffencesApiClient>()
  private val underTest = ManageOffencesService(mockManageOffencesApiClient)

  @Test
  fun getPcscMarkersForOffenceCodes() {
    whenever(mockManageOffencesApiClient.getPCSCMarkersForOffences(listOf(OFFENCE_CODE_SOME_PCSC_MARKERS))).thenReturn(listOf(dummyOffencePcscSomeMarkers))
    val testResult = underTest.getPcscMarkersForOffenceCodes(OFFENCE_CODE_SOME_PCSC_MARKERS)
    verify(mockManageOffencesApiClient, times(1)).getPCSCMarkersForOffences(listOf(OFFENCE_CODE_SOME_PCSC_MARKERS))
    assertThat(listOf(dummyOffencePcscSomeMarkers)).isEqualTo(testResult)
  }

  companion object {
    private const val OFFENCE_CODE_SOME_PCSC_MARKERS = "AV82002"
    private const val OFFENCE_CODE_NO_PCSC_MARKERS = ""
    private val dummyOffencePcscSomeMarkers = OffencePcscMarkers(
      offenceCode = OFFENCE_CODE_SOME_PCSC_MARKERS,
      pcscMarkers = PcscMarkers(
        inListA = true,
        inListB = false,
        inListC = false,
        inListD = true,
      ),
    )
    private val dummyOffencePcscNoMarkers = OffencePcscMarkers(
      offenceCode = OFFENCE_CODE_SOME_PCSC_MARKERS,
      pcscMarkers = PcscMarkers(
        inListA = false,
        inListB = false,
        inListC = false,
        inListD = false,
      ),
    )
  }
}
