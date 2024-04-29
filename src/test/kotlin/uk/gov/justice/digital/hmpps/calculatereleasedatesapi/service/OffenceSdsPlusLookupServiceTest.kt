package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
    assertTrue(returnedResult.filter { it.isSDSPlus }.size == 1)
    assertTrue(returnedResult[0].offences[0].isPcscSdsPlus)
    assertTrue(returnedResult[0].offences[0].isScheduleFifteenMaximumLife)
    assertTrue(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ Marker set for ADIMP Over 7 Years after PCSC and List D marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListDMarkers))
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(sentencedADIMPAfterPCSCLongerThan7Years)
    assertTrue(returnedResult[0].offences[0].isPcscSdsPlus)
    assertTrue(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ Marker set for YOI_ORA 4 to 7 years after PCSC and List B Marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListBMarkers))
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(sentencedYOIORAAfterPCSCLonger4To7Years)
    assertTrue(returnedResult[0].offences[0].isPcscSdsPlus)
    assertTrue(returnedResult[0].offences[0].isPcscSds)
    assertTrue(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ Marker set for SEC_250 Over 7 Years After PCSC and List C marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListCMarkers))
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(section250Over7YearsPostPCSCSentence)
    assertTrue(returnedResult[0].offences[0].isPcscSdsPlus)
    assertTrue(returnedResult[0].offences[0].isPcscSec250)
    assertTrue(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ is NOT set for SEC_250 Over 7 Years sentenced prior to PCSC`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListCMarkers))

    // no call to MO should take place as offences don't match filter.
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(section250Over7YearsPrePCSCSentence)
    assertFalse(returnedResult[0].offences[0].isPcscSdsPlus)
    assertFalse(returnedResult[0].offences[0].isPcscSec250)
    assertFalse(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ is NOT set for ADIMP as sentenced before SDS and not over 7 years in duration`() {
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(sentenceMatchesNoMatchingOffencesDueToSentenceDate)
    // no call to MO should take place as offences don't match filter.
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    assertFalse(returnedResult[0].offences[0].isPcscSdsPlus)
    assertFalse(returnedResult[0].offences[0].isPcscSec250)
    assertFalse(returnedResult[0].isSDSPlus)
  }

  @ParameterizedTest
  @CsvSource(
    "custom-examples/crs-1059-single-ersed-ac8,YOI_ORA,2021-02-25,3287,true,false,false,false",
    "custom-examples/crs-1081-ersed-consec-ac4,YOI_ORA,2022-05-06,2557,true,false,false,false",
    "custom-examples/crs-1081-ersed-consec-ac6,YOI_ORA,2021-11-03,3287,true,false,false,false",
    "custom-examples/crs-1109-ersed-bug-ac1,YOI_ORA,2020-12-15,4383,true,false,false,false",
    "custom-examples/crs-1145-ac3,YOI_ORA,2021-10-14,2739,true,false,false,false",
    "custom-examples/crs-1175-ac2,YOI_ORA,2021-10-14,2739,true,false,false,false",
    "custom-examples/crs-1317-bug,YOI_ORA,2021-10-14,2739,true,false,false,false",
    "custom-examples/crs-1736-ersed24-ac6,YOI_ORA,2023-01-13,1461,true,true,false,true",
    "custom-examples/crs-1740-ac2,YOI_ORA,2022-05-10,3776,true,false,false,true",
    "custom-examples/crs-1740-ac3,YOI_ORA,2023-01-25,3042,false,false,false,true",
    "custom-examples/crs-1740-ac4,YOI_ORA,2020-07-15,2556,true,false,false,true",
    "custom-examples/crs-1740-ac5-1,YOI_ORA,2023-07-16,1827,false,true,false,false",
    "custom-examples/crs-1740-ac5-1,YOI_ORA,2023-07-16,1461,false,true,false,false",
    "custom-examples/crs-1740-ac5,YOI_ORA,2023-01-25,3042,true,false,false,true",
    "custom-examples/crs-1740-ac6,YOI_ORA,2023-01-25,3042,true,false,false,true",
    "custom-examples/crs-1766-ersed24-ac4-2,YOI_ORA,2023-03-10,2192,false,true,false,false",
    "custom-examples/crs-1766-ersed24-ac4-2,YOI_ORA,2023-03-10,2557,false,false,false,true",
    "custom-examples/crs-377-sds-plus-example-1,YOI_ORA,2020-04-01,2556,true,false,false,false",
    "custom-examples/crs-377-sds-plus-row-15,YOI_ORA,2020-04-22,3835,true,false,false,false",
    "custom-examples/crs-377-sds-plus-row-47,YOI_ORA,2020-06-12,2556,true,false,false,false",
    "custom-examples/crs-658-sds-plus-consecutive-to-sds-plus,YOI_ORA,2020-04-01,2556,true,false,false,false",
    "custom-examples/crs-658-sds-plus-consecutive-to-sds-plus,YOI_ORA,2020-04-01,2556,true,false,false,false",
    "custom-examples/crs-658-sds-plus-consecutive-to-sds,YOI_ORA,2020-04-01,2922,true,false,false,false",
    "custom-examples/crs-680-pre-prod-1,YOI_ORA,2021-05-24,2557,true,false,false,false",
    "custom-examples/crs-685-tagged-bail-release-in-between-sentences,YOI_ORA,2022-09-09,3287,true,false,false,true",
    "custom-examples/crs-878-sds-sdsplus-consec-ac1,YOI_ORA,2022-05-05,2741,true,false,false,false",
    "custom-examples/crs-878-sds-sdsplus-consec-ac1,YOI_ORA,2022-05-05,3287,true,false,false,false",
    "custom-examples/crs-878-sds-sdsplus-consec-ac2,YOI_ORA,2022-05-05,2741,true,false,false,false",
    "custom-examples/crs-878-sds-sdsplus-consec-ac2,YOI_ORA,2022-05-05,3287,true,false,false,false",
    "custom-examples/crs-898-crd-mismatches,YOI_ORA,2020-12-11,2922,true,false,false,false",
    "custom-examples/crs-898-crd-mismatches,YOI_ORA,2020-12-11,3287,true,false,false,false",
    "custom-examples/crs-907-eds-sds-plus-ac1,YOI_ORA,2020-04-06,2556,true,false,false,false",
    "custom-examples/crs-907-eds-sds-plus-ac2,YOI_ORA,2020-09-16,2922,true,false,false,false",
    "custom-examples/crs-921-pcsc-four-to-under-seven-ac1,YOI_ORA,2022-06-28,1461,false,true,false,false",
    "custom-examples/crs-921-pcsc-sec250-ac1,SEC250_ORA,2022-06-28,4383,false,false,true,false",
    "custom-examples/crs-921-pcsc-updated-ac1,YOI_ORA,2022-06-28,4383,false,false,false,true",
    "custom-examples/crs-925-pcsc-tests-ac1,YOI_ORA,2022-06-28,1461,false,true,false,false",
    "custom-examples/crs-925-pcsc-tests-ac2,SEC250_ORA,2022-06-28,2557,false,false,true,false",
    "custom-examples/crs-925-pcsc-tests-ac3,YOI_ORA,2022-06-27,2557,true,false,false,false",
    "custom-examples/crs-925-pcsc-tests-ac3,YOI_ORA,2022-06-28,2557,false,false,false,true",
    "custom-examples/crs-950-pre-pcsc-sopc-ac5,YOI_ORA,2021-11-03,3287,true,false,false,false",
    "framework-examples/2,YOI_ORA,2024-05-10,3775,false,false,false,true",
    "framework-examples/33,YOI_ORA,2023-01-25,3042,false,false,false,true",
    "alternative-release-point/21,YOI_ORA,2022-08-26,1461,true,true,true,true",
    "alternative-release-point/22,YOI_ORA,2023-07-28,1461,true,true,true,true",
  )
  fun `should produce same result as old tests`(name: String, sentenceCalculationType: SentenceCalculationType, sentenceDate: LocalDate, sentenceLengthDays: Int, inListA: Boolean, inListB: Boolean, inListC: Boolean, inListD: Boolean) {
    val markers = OffencePcscMarkers(
      offenceCode = OFFENCE_CODE_SOME_PCSC_MARKERS,
      pcscMarkers = PcscMarkers(
        inListA = inListA,
        inListB = inListB,
        inListC = inListC,
        inListD = inListD,
      ),
    )
    val sentence = PrisonApiSentenceAndOffences(
      1,
      1,
      1,
      1,
      null,
      "TEST",
      "TEST",
      sentenceCalculationType.toString(),
      "TEST",
      sentenceDate,
      listOf(SentenceTerms(0, 0, 0, sentenceLengthDays)),
      listOf(
        OffenderOffence(
          1,
          LocalDate.of(2020, 4, 1),
          null,
          OFFENCE_CODE_SOME_PCSC_MARKERS,
          "TEST OFFENCE 2",
        ),
      ),
    )
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(markers))
    val returnedResult = underTest.populateSdsPlusMarkerForOffences(listOf(sentence))
    assertTrue(returnedResult.filter { it.isSDSPlus }.size == 1, "Failed for $name")
    assertTrue(returnedResult[0].isSDSPlus, "Failed for $name")
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
