package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.*
import java.time.LocalDate

class OffenceSdsPlusLookupServiceTest {

    private val mockManageOffencesService = mock<ManageOffencesService>();
    private val mockPrisonService = mock<PrisonService>();

    private val underTest = OffenceSdsPlusLookupService(mockManageOffencesService, mockPrisonService);

    @Test
    fun testSetSdsPlusMarkerForMatchingSingularSentenceAndSingularOffence() {
        whenever(mockPrisonService.getOffenderDetail(any())).thenReturn(PrisonerDetails(bookingId = 1, offenderNo = "123", dateOfBirth = LocalDate.of(1971, 1, 1)))
        whenever(mockPrisonService.getSentencesAndOffences(1)).thenReturn(sentenceMatchesSDSCriteriaOneOffence)
        assertEquals(listOf("A123456"), underTest.setSdsPlusMarkerForOffences("123"))
    }

    @Test
    fun testSetSdsPlusMarkerForMatchingSingularSentenceAndTwoOffences() {
        whenever(mockPrisonService.getOffenderDetail(any())).thenReturn(PrisonerDetails(bookingId = 1, offenderNo = "123", dateOfBirth = LocalDate.of(1971, 1, 1)))
        whenever(mockPrisonService.getSentencesAndOffences(1)).thenReturn(sentenceMatchesSDSCriteriaTwoOffences)
        assertEquals(listOf("A123456", "A000000"), underTest.setSdsPlusMarkerForOffences("123"))
    }

    @Test
    fun testSetSdsPlusMarkerForMatchingMultipleMatchingSentencesAndTwoOffences() {
        whenever(mockPrisonService.getOffenderDetail(any())).thenReturn(PrisonerDetails(bookingId = 1, offenderNo = "123", dateOfBirth = LocalDate.of(1971, 1, 1)))
        whenever(mockPrisonService.getSentencesAndOffences(1)).thenReturn(multipleSentencesMatchesSDSCriteriaMultipleOffences)
        assertEquals(listOf("A123456", "A000000","A111111", "A22222"), underTest.setSdsPlusMarkerForOffences("123"))
    }

    @Test
    fun testSetSdsPlusMarkerForMatchingOneMatchingSentencesOneNotMatchingSentenceOnDate() {
        whenever(mockPrisonService.getOffenderDetail(any())).thenReturn(PrisonerDetails(bookingId = 1, offenderNo = "123", dateOfBirth = LocalDate.of(1971, 1, 1)))
        whenever(mockPrisonService.getSentencesAndOffences(1)).thenReturn(oneSentencesMatchesSDSCriteriaOneDoesNotMatchOnDateMultipleOffences)
        assertEquals(listOf("A111111", "A22222"), underTest.setSdsPlusMarkerForOffences("123"))
    }

    companion object {
        private val sentenceMatchesSDSCriteriaOneOffence = listOf(SentenceAndOffences(1,
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
                listOf(OffenderOffence(
                        1,
                        LocalDate.of(2020, 4, 1),
                        null,
                        "A123456",
                        "TEST OFFENSE")
                )))

        private val sentenceMatchesSDSCriteriaTwoOffences = listOf(SentenceAndOffences(1,
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
                listOf(OffenderOffence(
                        1,
                        LocalDate.of(2020, 4, 1),
                        null,
                        "A123456",
                        "TEST OFFENCE 1"),
                        OffenderOffence(
                                1,
                                LocalDate.of(2020, 4, 1),
                                null,
                                "A000000",
                                "TEST OFFENCE 2")
                )))

        private val multipleSentencesMatchesSDSCriteriaMultipleOffences = listOf(SentenceAndOffences(1,
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
                listOf(OffenderOffence(
                        1,
                        LocalDate.of(2020, 4, 1),
                        null,
                        "A123456",
                        "TEST OFFENCE 1"),
                        OffenderOffence(
                                1,
                                LocalDate.of(2020, 4, 1),
                                null,
                                "A000000",
                                "TEST OFFENCE 2")
                )),
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
                        listOf(OffenderOffence(
                                1,
                                LocalDate.of(2020, 4, 1),
                                null,
                                "A111111",
                                "TEST OFFENCE 1"),
                                OffenderOffence(
                                        1,
                                        LocalDate.of(2020, 4, 1),
                                        null,
                                        "A22222",
                                        "TEST OFFENCE 2")
                        )))

        private val oneSentencesMatchesSDSCriteriaOneDoesNotMatchOnDateMultipleOffences = listOf(SentenceAndOffences(1,
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
                listOf(OffenderOffence(
                        1,
                        LocalDate.of(2020, 4, 1),
                        null,
                        "A123456",
                        "TEST OFFENCE 1"),
                        OffenderOffence(
                                1,
                                LocalDate.of(2020, 4, 1),
                                null,
                                "A000000",
                                "TEST OFFENCE 2")
                )),
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
                        listOf(OffenderOffence(
                                1,
                                LocalDate.of(2020, 4, 1),
                                null,
                                "A111111",
                                "TEST OFFENCE 1"),
                                OffenderOffence(
                                        1,
                                        LocalDate.of(2020, 4, 1),
                                        null,
                                        "A22222",
                                        "TEST OFFENCE 2")
                        )))
    }

//    val bookingId: Long,
//    val sentenceSequence: Int,
//    val lineSequence: Int,
//    val caseSequence: Int,
//    val consecutiveToSequence: Int? = null,
//    val sentenceStatus: String,
//    val sentenceCategory: String,
//    val sentenceCalculationType: String,
//    val sentenceTypeDescription: String,
//    val sentenceDate: LocalDate,
//    val terms: List<SentenceTerms> = emptyList(),
//    val offences: List<OffenderOffence> = emptyList(),
//    val caseReference: String? = null,
//    val courtDescription: String? = null,
//    val fineAmount: BigDecimal? = null,

}