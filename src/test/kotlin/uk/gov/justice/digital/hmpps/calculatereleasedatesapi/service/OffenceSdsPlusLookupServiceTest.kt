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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
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
    assertTrue(returnedResult.size == 1)
    assertTrue(sentencedAfterSDSPlusBeforePCSCLongerThan7Years[0].offences[0].isPcscSdsPlus)
  }

  @Test
  fun `SDS+ Marker set for ADIMP Over 7 Years after PCSC and List D marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListDMarkers))
    underTest.populateSdsPlusMarkerForOffences(sentencedADIMPAfterPCSCLongerThan7Years)
    assertTrue(sentencedADIMPAfterPCSCLongerThan7Years[0].offences[0].isPcscSdsPlus)
  }

  @Test
  fun `SDS+ Marker set for YOI_ORA 4 to 7 years after PCSC and List B Marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListBMarkers))
    underTest.populateSdsPlusMarkerForOffences(sentencedYOIORAAfterPCSCLonger4To7Years)
    assertTrue(sentencedYOIORAAfterPCSCLonger4To7Years[0].offences[0].isPcscSdsPlus)
  }

  @Test
  fun `SDS+ Marker set for SEC_250 Over 7 Years After PCSC and List C marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListCMarkers))
    underTest.populateSdsPlusMarkerForOffences(section250Over7YearsPostPCSCSentence)
    assertTrue(section250Over7YearsPostPCSCSentence[0].offences[0].isPcscSdsPlus)
  }

  @Test
  fun `SDS+ is NOT set for SEC_250 Over 7 Years sentenced prior to PCSC`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListCMarkers))

    // no call to MO should take place as offences don't match filter.
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    underTest.populateSdsPlusMarkerForOffences(section250Over7YearsPrePCSCSentence)
    assertFalse(section250Over7YearsPrePCSCSentence[0].offences[0].isPcscSdsPlus)
  }

  @Test
  fun `SDS+ is NOT set for ADIMP as sentenced before SDS and not over 7 years in duration`() {
    underTest.populateSdsPlusMarkerForOffences(sentenceMatchesNoMatchingOffencesDueToSentenceDate)
    // no call to MO should take place as offences don't match filter.
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    assertFalse(sentenceMatchesNoMatchingOffencesDueToSentenceDate[0].offences[0].isPcscSdsPlus)
  }

  companion object {
    private val sentenceMatchesNoMatchingOffencesDueToSentenceDate = listOf(
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
      SentenceAndOffences(
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
