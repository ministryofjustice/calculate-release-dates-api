package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SDSEarlyReleaseExclusionForOffenceCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SDSEarlyReleaseExclusionSchedulePart
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.time.LocalDate

class OffenceSDSReleaseArrangementLookupServiceTest {

  private val mockManageOffencesService = mock<ManageOffencesService>()

  private val underTest = OffenceSDSReleaseArrangementLookupService(mockManageOffencesService)

  @Test
  fun `SDS+ Marker set when sentenced after SDS and before PCSC and sentence longer than 7 Years with singular offence - List A`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListAMarkers))
    val returnedResult = underTest.populateReleaseArrangements(sentencedAfterSDSPlusBeforePCSCLongerThan7Years)
    assertTrue(returnedResult.filter { it.isSDSPlus }.size == 1)
    assertTrue(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ Marker set for ADIMP Over 7 Years after PCSC and List D marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListDMarkers))
    val returnedResult = underTest.populateReleaseArrangements(sentencedADIMPAfterPCSCLongerThan7Years)
    assertTrue(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ Marker set for YOI_ORA 4 to 7 years after PCSC and List B Marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListBMarkers))
    val returnedResult = underTest.populateReleaseArrangements(sentencedYOIORAAfterPCSCLonger4To7Years)
    assertTrue(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ Marker set for SEC_250 Over 7 Years After PCSC and List C marker returned`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListCMarkers))
    val returnedResult = underTest.populateReleaseArrangements(section250Over7YearsPostPCSCSentence)
    assertTrue(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ is NOT set for SEC_250 Over 7 Years sentenced prior to PCSC`() {
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListCMarkers))

    // no call to MO should take place as offences don't match filter.
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    val returnedResult = underTest.populateReleaseArrangements(section250Over7YearsPrePCSCSentence)
    assertFalse(returnedResult[0].isSDSPlus)
  }

  @Test
  fun `SDS+ is NOT set for ADIMP as sentenced before SDS and not over 7 years in duration`() {
    val returnedResult = underTest.populateReleaseArrangements(sentenceMatchesNoMatchingOffencesDueToSentenceDate)
    // no call to MO should take place as offences don't match filter.
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
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
    val sentence = NormalisedSentenceAndOffence(
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
      OffenderOffence(
        1,
        LocalDate.of(2020, 4, 1),
        null,
        OFFENCE_CODE_SOME_PCSC_MARKERS,
        "TEST OFFENCE 2",
      ),
      null,
      null,
      null,
    )
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(markers))
    val returnedResult = underTest.populateReleaseArrangements(listOf(sentence))
    assertTrue(returnedResult.filter { it.isSDSPlus }.size == 1, "Failed for $name")
    assertTrue(returnedResult[0].isSDSPlus, "Failed for $name")
  }

  @Test
  fun `should not set an SDS exclusion if offence is neither sexual or violent`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))).thenReturn(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode(OFFENCE_CODE_NON_SDS_PLUS, SDSEarlyReleaseExclusionSchedulePart.NONE),
      ),
    )
    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(nonSDSPlusSentenceAndOffenceFourYears))
    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.NO)
    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    verify(mockManageOffencesService).getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))
  }

  @Test
  fun `should set has an SDS exclusion if offence is sexual`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))).thenReturn(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode(OFFENCE_CODE_NON_SDS_PLUS, SDSEarlyReleaseExclusionSchedulePart.SEXUAL),
      ),
    )

    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(nonSDSPlusSentenceAndOffenceFourYears))
    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.SEXUAL)

    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    verify(mockManageOffencesService).getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))
  }

  @Test
  fun `should set has an SDS exclusion if offence is violent and 4 years or more`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))).thenReturn(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode(OFFENCE_CODE_NON_SDS_PLUS, SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
      ),
    )

    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(nonSDSPlusSentenceAndOffenceFourYears))
    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.VIOLENT)

    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    verify(mockManageOffencesService).getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))
  }

  @Test
  fun `should not set an SDS exclusion if offence is violent but under 4 years`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))).thenReturn(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode(OFFENCE_CODE_NON_SDS_PLUS, SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
      ),
    )

    val nonSDSPlusSentenceLessThanFourYears = nonSDSPlusSentenceAndOffenceFourYears.copy(terms = listOf(SentenceTerms(3, 11, 0, 0)))
    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(nonSDSPlusSentenceLessThanFourYears))
    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.NO)

    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    verify(mockManageOffencesService).getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))
  }

  @Test
  fun `should not set an SDS exclusion if offence is not returned from MO`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))).thenReturn(emptyList())

    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(nonSDSPlusSentenceAndOffenceFourYears))
    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.NO)

    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    verify(mockManageOffencesService).getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))
  }

  @Test
  fun `should not set an SDS exclusion if sentence is unsupported`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))).thenReturn(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode(OFFENCE_CODE_NON_SDS_PLUS, SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
      ),
    )

    val unsupportedSentence = nonSDSPlusSentenceAndOffenceFourYears.copy(sentenceCalculationType = SentenceCalculationType.entries.find { !it.isSupported }!!.name)
    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(unsupportedSentence))
    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.NO)

    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    verify(mockManageOffencesService, times(0)).getSexualOrViolentForOffenceCodes(any())
  }

  @Test
  fun `should not set an SDS exclusion if sentence is supported but not in SDS sentence types`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf(OFFENCE_CODE_NON_SDS_PLUS))).thenReturn(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode(OFFENCE_CODE_NON_SDS_PLUS, SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
      ),
    )

    val unsupportedSentence = nonSDSPlusSentenceAndOffenceFourYears.copy(sentenceCalculationType = SentenceCalculationType.EDS18.name)
    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(unsupportedSentence))
    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.NO)

    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    verify(mockManageOffencesService, times(0)).getSexualOrViolentForOffenceCodes(any())
  }

  @Test
  fun `should match codes for sexual and violent against correct offences`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf("SX01", "V01"))).thenReturn(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode("SX01", SDSEarlyReleaseExclusionSchedulePart.SEXUAL),
        SDSEarlyReleaseExclusionForOffenceCode("V01", SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
      ),
    )

    val sexualOffenceSentence = nonSDSPlusSentenceAndOffenceFourYears.copy(offence = nonSDSPlusSentenceAndOffenceFourYears.offence.copy(offenceCode = "SX01"), sentenceSequence = 100)
    val violentOffenceSentence = nonSDSPlusSentenceAndOffenceFourYears.copy(offence = nonSDSPlusSentenceAndOffenceFourYears.offence.copy(offenceCode = "V01"), sentenceSequence = 200)
    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(sexualOffenceSentence, violentOffenceSentence))

    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.SEXUAL)
    assertThat(withReleaseArrangements[0].sentenceSequence).isEqualTo(100)
    assertThat(withReleaseArrangements[1].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[1].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.VIOLENT)
    assertThat(withReleaseArrangements[1].sentenceSequence).isEqualTo(200)

    verify(mockManageOffencesService, times(0)).getPcscMarkersForOffenceCodes(any())
    verify(mockManageOffencesService).getSexualOrViolentForOffenceCodes(listOf("SX01", "V01"))
  }

  @Test
  fun `should not check for sexual or violent if it's SDS plus`() {
    whenever(mockManageOffencesService.getSexualOrViolentForOffenceCodes(listOf("SX01", "V01"))).thenReturn(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode("SX01", SDSEarlyReleaseExclusionSchedulePart.SEXUAL),
        SDSEarlyReleaseExclusionForOffenceCode("V01", SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
      ),
    )
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(pcscListAMarkers))

    val sexualOffenceSentence = nonSDSPlusSentenceAndOffenceFourYears.copy(offence = nonSDSPlusSentenceAndOffenceFourYears.offence.copy(offenceCode = "SX01"), sentenceSequence = 100)
    val violentOffenceSentence = nonSDSPlusSentenceAndOffenceFourYears.copy(offence = nonSDSPlusSentenceAndOffenceFourYears.offence.copy(offenceCode = "V01"), sentenceSequence = 200)
    val sdsPlusOffence = sentencedAfterSDSPlusBeforePCSCLongerThan7Years[0].copy(sentenceSequence = 300)
    val withReleaseArrangements = underTest.populateReleaseArrangements(listOf(sexualOffenceSentence, violentOffenceSentence, sdsPlusOffence))

    assertThat(withReleaseArrangements[0].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[0].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.SEXUAL)
    assertThat(withReleaseArrangements[0].sentenceSequence).isEqualTo(100)
    assertThat(withReleaseArrangements[1].isSDSPlus).isFalse()
    assertThat(withReleaseArrangements[1].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.VIOLENT)
    assertThat(withReleaseArrangements[1].sentenceSequence).isEqualTo(200)
    assertThat(withReleaseArrangements[2].isSDSPlus).isTrue()
    assertThat(withReleaseArrangements[2].hasAnSDSEarlyReleaseExclusion).isEqualTo(SDSEarlyReleaseExclusionType.NO)
    assertThat(withReleaseArrangements[2].sentenceSequence).isEqualTo(300)

    verify(mockManageOffencesService).getPcscMarkersForOffenceCodes(OFFENCE_CODE_SOME_PCSC_MARKERS)
    verify(mockManageOffencesService).getSexualOrViolentForOffenceCodes(listOf("SX01", "V01"))
  }

  @ParameterizedTest
  @CsvSource(
    // sentence date
    "DV04001,ADIMP,2022-06-28,7,true",
    "DV04001,ADIMP,2022-06-29,7,true",
    "DV04001,ADIMP,2022-06-27,7,false",
    // sentence length
    "DV04001,ADIMP,2022-06-28,8,true",
    "DV04001,ADIMP,2022-06-28,6,false",
    // sentence length and date mismatch
    "DV04001,ADIMP,2022-06-27,6,false",
    // code in list
    "DV04001,ADIMP,2022-06-28,8,true",
    "RT88001,ADIMP,2022-06-28,8,true",
    "RT88500,ADIMP,2022-06-28,8,true",
    "RT88527,ADIMP,2022-06-28,8,true",
    "RT88338,ADIMP,2022-06-28,8,true",
    "RT88583,ADIMP,2022-06-28,8,true",
    "RA88043,ADIMP,2022-06-28,8,true",
    "RT88337,ADIMP,2022-06-28,8,true",
    "RT88554,ADIMP,2022-06-28,8,true",
    "RT88029,ADIMP,2022-06-28,8,true",
    "RT88502,ADIMP,2022-06-28,8,true",
    "RT88028,ADIMP,2022-06-28,8,true",
    "RT88027,ADIMP,2022-06-28,8,true",
    "RT88579,ADIMP,2022-06-28,8,true",
    "NOTSPECIAL,ADIMP,2022-06-28,8,false",
    // sentence type
    "DV04001,ADIMP,2022-06-29,7,true",
    "DV04001,ADIMP_ORA,2022-06-29,7,true",
    "DV04001,YOI,2022-06-29,7,true",
    "DV04001,YOI_ORA,2022-06-29,7,true",
    "DV04001,SEC250,2022-06-29,7,false",
    "DV04001,SEC250_ORA,2022-06-29,7,false",
    "DV04001,EDS21,2022-06-29,7,false",
    // sentence type unsupported
    "DV04001,A_FINE,2022-06-29,7,false",
  )
  fun `should mark old offence codes as SDS+ if the sentence is more than 7 years after PCSC date`(offenceCode: String, sentenceCalculationType: SentenceCalculationType, sentenceDate: LocalDate, sentenceLengthYears: Int, isSDSPlus: Boolean) {
    val markers = OffencePcscMarkers(
      offenceCode = offenceCode,
      pcscMarkers = PcscMarkers(
        inListA = false,
        inListB = false,
        inListC = false,
        inListD = false,
      ),
    )
    val sentence = NormalisedSentenceAndOffence(
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
      listOf(SentenceTerms(sentenceLengthYears, 0, 0, 0)),
      OffenderOffence(
        1,
        LocalDate.of(2020, 4, 1),
        null,
        offenceCode,
        "TEST OFFENCE 2",
      ),
      null,
      null,
      null,
    )
    whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(markers))
    val returnedResult = underTest.populateReleaseArrangements(listOf(sentence))
    assertThat(returnedResult[0].isSDSPlus).describedAs("Expected isSDSPlus to be $isSDSPlus").isEqualTo(isSDSPlus)
  }

  @ParameterizedTest
  @CsvSource(
    "DV04001,true",
    "RT88001,true",
    "RT88500,true",
    "RT88527,true",
    "RT88338,true",
    "RT88583,true",
    "RA88043,true",
    "RT88337,true",
    "RT88554,true",
    "RT88029,true",
    "RT88502,true",
    "RT88028,true",
    "RT88027,true",
    "RT88579,true",
    "FOOOOOO,false",
  )
  fun `old offence codes also consider suffix variants A, B, C or I`(offenceCodeWithoutSuffix: String, isSDSPlus: Boolean) {
    val offenceCodeWithSuffixes = listOf(offenceCodeWithoutSuffix, "${offenceCodeWithoutSuffix}A", "${offenceCodeWithoutSuffix}B", "${offenceCodeWithoutSuffix}C", "${offenceCodeWithoutSuffix}I")
    offenceCodeWithSuffixes.forEach { offenceCode ->
      val markers = OffencePcscMarkers(
        offenceCode = offenceCode,
        pcscMarkers = PcscMarkers(
          inListA = false,
          inListB = false,
          inListC = false,
          inListD = false,
        ),
      )
      val sentence = NormalisedSentenceAndOffence(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        "ADIMP",
        "TEST",
        LocalDate.of(3033, 6, 28),
        listOf(SentenceTerms(7, 0, 0, 0)),
        OffenderOffence(
          1,
          LocalDate.of(2020, 4, 1),
          null,
          offenceCode,
          "TEST OFFENCE 2",
        ),
        null,
        null,
        null,
      )
      whenever(mockManageOffencesService.getPcscMarkersForOffenceCodes(any())).thenReturn(listOf(markers))
      val returnedResult = underTest.populateReleaseArrangements(listOf(sentence))
      assertThat(returnedResult[0].isSDSPlus).describedAs("Expected isSDSPlus to be $isSDSPlus for code $offenceCode").isEqualTo(isSDSPlus)
    }
  }

  companion object {
    private const val OFFENCE_CODE_NON_SDS_PLUS = "Z654321"

    private val sentenceMatchesNoMatchingOffencesDueToSentenceDate = listOf(
      NormalisedSentenceAndOffence(
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
        OffenderOffence(
          1,
          LocalDate.of(2020, 3, 31),
          null,
          "A123456",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
    )

    private val sentencedAfterSDSPlusBeforePCSCLongerThan7Years = listOf(
      NormalisedSentenceAndOffence(
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
        OffenderOffence(
          1,
          LocalDate.of(2021, 4, 1),
          null,
          "A123456",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
    )

    private val sentencedADIMPAfterPCSCLongerThan7Years = listOf(
      NormalisedSentenceAndOffence(
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
        OffenderOffence(
          1,
          LocalDate.of(2022, 6, 29),
          null,
          "A123456",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
    )

    private val sentencedYOIORAAfterPCSCLonger4To7Years = listOf(
      NormalisedSentenceAndOffence(
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
        OffenderOffence(
          1,
          LocalDate.of(2023, 1, 1),
          null,
          "A123456",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
    )

    private val section250Over7YearsPostPCSCSentence = listOf(
      NormalisedSentenceAndOffence(
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
        OffenderOffence(
          1,
          LocalDate.of(2022, 1, 1),
          null,
          "A123456",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
    )

    private val section250Over7YearsPrePCSCSentence = listOf(
      NormalisedSentenceAndOffence(
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
        OffenderOffence(
          1,
          LocalDate.of(2020, 1, 1),
          null,
          "A123456",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
    )
    val nonSDSPlusSentenceAndOffenceFourYears = NormalisedSentenceAndOffence(
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
      listOf(SentenceTerms(4, 0, 0, 0)),
      OffenderOffence(
        1,
        LocalDate.of(2020, 3, 31),
        null,
        OFFENCE_CODE_NON_SDS_PLUS,
        "TEST OFFENSE",
      ),
      null,
      null,
      null,
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
