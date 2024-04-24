package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.time.LocalDate

class OffenceSdsPlusLookupServiceTest {

  private val mockManageOffencesService = mock<ManageOffencesService>()

  private val underTest = OffenceSdsPlusLookupService(mockManageOffencesService)

  @Test
  fun `SDS+ Marker set when sentenced after SDS and before PCSC and sentence longer than 7 Years with singular offence - List A`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListAMarkers))
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(sentencedAfterSDSPlusBeforePCSCLongerThan7Years)
    assertTrue(returnedResult.filter { it.isSdsPlus }.size == 1)
    assertTrue(returnedResult[0].offences[0].isPcscSdsPlus)
    assertTrue(returnedResult[0].offences[0].isScheduleFifteenMaximumLife)
    assertTrue(returnedResult[0].isSdsPlus)
  }

  @Test
  fun `SDS+ Marker set for ADIMP Over 7 Years after PCSC and List D marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListDMarkers))
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(sentencedADIMPAfterPCSCLongerThan7Years)
    assertTrue(returnedResult[0].offences[0].isPcscSdsPlus)
    assertTrue(returnedResult[0].offences[0].isPcscSds)
    assertTrue(returnedResult[0].isSdsPlus)
  }

  @Test
  fun `SDS+ Marker set for YOI_ORA 4 to 7 years after PCSC and List B Marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListBMarkers))
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(sentencedYOIORAAfterPCSCLonger4To7Years)
    assertTrue(returnedResult[0].offences[0].isPcscSdsPlus)
    assertTrue(returnedResult[0].isSdsPlus)
  }

  @Test
  fun `SDS+ Marker set for SEC_250 Over 7 Years After PCSC and List C marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListCMarkers))
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(section250Over7YearsPostPCSCSentence)
    assertTrue(returnedResult[0].offences[0].isPcscSdsPlus)
    assertTrue(returnedResult[0].offences[0].isPcscSec250)
    assertTrue(returnedResult[0].isSdsPlus)
  }

  @Test
  fun `SDS+ is NOT set for SEC_250 Over 7 Years sentenced prior to PCSC`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListCMarkers))

    // no call to MO should take place as offences don't match filter.
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(section250Over7YearsPrePCSCSentence)
    assertFalse(returnedResult[0].offences[0].isPcscSdsPlus)
    assertFalse(returnedResult[0].offences[0].isPcscSec250)
    assertFalse(returnedResult[0].isSdsPlus)
  }

  @Test
  fun `SDS+ is NOT set for ADIMP as sentenced before SDS and not over 7 years in duration`() {
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(sentenceMatchesNoMatchingOffencesDueToSentenceDate)
    // no call to MO should take place as offences don't match filter.
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    assertFalse(returnedResult[0].offences[0].isPcscSdsPlus)
    assertFalse(returnedResult[0].offences[0].isPcscSec250)
    assertFalse(returnedResult[0].isSdsPlus)
  }

  companion object {
    private val sentenceMatchesNoMatchingOffencesDueToSentenceDate = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.ADIMP.toString(),
        "TEST",
        LocalDate.of(2020, 3, 31),
        listOf(SentenceTerms(4, 1, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2020, 3, 31),
            null,
            "A123456",
            "TEST OFFENSE",
          ),
        ),
      ),
    )

    private val sentencedAfterSDSPlusBeforePCSCLongerThan7Years = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.YOI.toString(),
        "TEST",
        LocalDate.of(2021, 4, 2),
        listOf(SentenceTerms(12, 4, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2021, 4, 1),
            null,
            "A123456",
            "TEST OFFENSE",
          ),
        ),
      ),
    )

    private val sentencedADIMPAfterPCSCLongerThan7Years = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.ADIMP.toString(),
        "TEST",
        LocalDate.of(2022, 6, 29),
        listOf(SentenceTerms(12, 4, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2022, 6, 29),
            null,
            "A123456",
            "TEST OFFENSE",
          ),
        ),
      ),
    )

    private val sentencedYOIORAAfterPCSCLonger4To7Years = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.YOI_ORA.toString(),
        "TEST",
        LocalDate.of(2023, 2, 1),
        listOf(SentenceTerms(5, 2, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2023, 1, 1),
            null,
            "A123456",
            "TEST OFFENSE",
          ),
        ),
      ),
    )

    private val section250Over7YearsPostPCSCSentence = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.SEC250.toString(),
        "TEST",
        LocalDate.of(2022, 8, 29),
        listOf(SentenceTerms(8, 4, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2022, 1, 1),
            null,
            "A123456",
            "TEST OFFENSE",
          ),
        ),
      ),
    )

    private val section250Over7YearsPrePCSCSentence = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.SEC250.toString(),
        "TEST",
        LocalDate.of(2020, 8, 29),
        listOf(SentenceTerms(8, 4, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2020, 1, 1),
            null,
            "A123456",
            "TEST OFFENSE",
          ),
        ),
      ),
    )

    private val sentenceMatchesSDSCriteriaTwoOffences = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.ADIMP.toString(),
        "TEST",
        LocalDate.of(2020, 4, 2),
        listOf(SentenceTerms(4, 1, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A123456",
            "TEST OFFENCE 1",
          ),
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A000000",
            "TEST OFFENCE 2",
          ),
        ),
      ),
    )

    private val multipleSentencesMatchesSDSCriteriaMultipleOffences = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.ADIMP.toString(),
        "TEST",
        LocalDate.of(2020, 4, 2),
        listOf(SentenceTerms(4, 1, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A123456",
            "TEST OFFENCE 1",
          ),
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A000000",
            "TEST OFFENCE 2",
          ),
        ),
      ),
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.SEC250.toString(),
        "TEST",
        LocalDate.of(2020, 4, 2),
        listOf(SentenceTerms(4, 1, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A111111",
            "TEST OFFENCE 1",
          ),
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A22222",
            "TEST OFFENCE 2",
          ),
        ),
      ),
    )

    private val oneSentencesMatchesSDSCriteriaOneDoesNotMatchOnDateMultipleOffences = listOf(
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.YOI_ORA.toString(),
        "TEST",
        LocalDate.of(2020, 3, 31),
        listOf(SentenceTerms(4, 1, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A123456",
            "TEST OFFENCE 1",
          ),
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A000000",
            "TEST OFFENCE 2",
          ),
        ),
      ),
      PrisonApiSentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.ADIMP.toString(),
        "TEST",
        LocalDate.of(2020, 4, 2),
        listOf(SentenceTerms(12, 1, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A111111",
            "TEST OFFENCE 1",
          ),
          OffenderOffence(
            1,
            LocalDate.of(2020, 4, 1),
            null,
            "A22222",
            "TEST OFFENCE 2",
          ),
        ),
      ),
    )

    private const val OFFENCE_CODE_SOME_PCSC_MARKERS = "A123456"

    private val pcscListAMarkers = OffencePcscMarkers(
      offenceCode = OFFENCE_CODE_SOME_PCSC_MARKERS,
      pcscMarkers = PcscMarkers(
        inListA = true,
        inListB = false,
        inListC = false,
        inListD = false,
      ),
    )

    private val pcscListDMarkers = OffencePcscMarkers(
      offenceCode = OFFENCE_CODE_SOME_PCSC_MARKERS,
      pcscMarkers = PcscMarkers(
        inListA = false,
        inListB = false,
        inListC = false,
        inListD = true,
      ),
    )

    private val pcscListBMarkers = OffencePcscMarkers(
      offenceCode = OFFENCE_CODE_SOME_PCSC_MARKERS,
      pcscMarkers = PcscMarkers(
        inListA = false,
        inListB = true,
        inListC = false,
        inListD = false,
      ),
    )

    private val pcscListCMarkers = OffencePcscMarkers(
      offenceCode = OFFENCE_CODE_SOME_PCSC_MARKERS,
      pcscMarkers = PcscMarkers(
        inListA = false,
        inListB = false,
        inListC = true,
        inListD = false,
      ),
    )
  }
}
