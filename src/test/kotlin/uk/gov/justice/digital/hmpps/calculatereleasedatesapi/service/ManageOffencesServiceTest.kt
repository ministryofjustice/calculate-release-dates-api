package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionSchedulePart

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

  @Test
  fun getSdsExclusionsForOffenceCodes() {
    val moResult = listOf(
      SDSEarlyReleaseExclusionForOffenceCode("SX01", SDSEarlyReleaseExclusionSchedulePart.SEXUAL),
      SDSEarlyReleaseExclusionForOffenceCode("V01", SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
      SDSEarlyReleaseExclusionForOffenceCode("N01", SDSEarlyReleaseExclusionSchedulePart.NONE),
    )
    whenever(mockManageOffencesApiClient.getSdsExclusionsForOffenceCodes(listOf("SX01", "V01", "N01"))).thenReturn(moResult)
    val testResult = underTest.getSdsExclusionsForOffenceCodes(listOf("SX01", "V01", "N01"))
    verify(mockManageOffencesApiClient, times(1)).getSdsExclusionsForOffenceCodes(listOf("SX01", "V01", "N01"))
    assertThat(testResult).isEqualTo(moResult)
  }

  companion object {
    private const val OFFENCE_CODE_SOME_PCSC_MARKERS = "AV82002"
    private val dummyOffencePcscSomeMarkers = OffencePcscMarkers(
      offenceCode = OFFENCE_CODE_SOME_PCSC_MARKERS,
      pcscMarkers = PcscMarkers(
        inListA = true,
        inListB = false,
        inListC = false,
        inListD = true,
      ),
    )
  }
}
