package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DeletedBulkComparisonsResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonToDelete
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ComparisonDiscrepancyServiceTest.Companion.USERNAME
import java.time.LocalDateTime
import java.time.ZoneOffset

class DeleteOldBulkCalculationsServiceTest {
  private val objectMapper: ObjectMapper = TestUtil.objectMapper()
  private val comparisonRepository: ComparisonRepository = mock()
  private val comparisonPersonRepository: ComparisonPersonRepository = mock()
  private val deleteCalculationService: DeleteCalculationService = mock()
  private val service = DeleteOldBulkCalculationsService(comparisonRepository, comparisonPersonRepository, deleteCalculationService)

  @Test
  fun `should delete multiple bulk calcs if they are all small`() {
    val comparisonToDelete1 = ComparisonToDelete(1, 40, LocalDateTime.now().minusDays(20).toInstant(ZoneOffset.UTC))
    val comparisonToDelete2 = ComparisonToDelete(2, 40, LocalDateTime.now().minusDays(21).toInstant(ZoneOffset.UTC))
    val comparisonToDelete3 = ComparisonToDelete(3, 40, LocalDateTime.now().minusDays(22).toInstant(ZoneOffset.UTC))
    val comparisonToDelete4 = ComparisonToDelete(4, 40, LocalDateTime.now().minusDays(23).toInstant(ZoneOffset.UTC))

    whenever(comparisonRepository.allComparisonsOlderThanDateExceptTheLatestTwo(any())).thenReturn(listOf(comparisonToDelete1, comparisonToDelete2, comparisonToDelete3, comparisonToDelete4))
    whenever(comparisonPersonRepository.findByComparisonId(1)).thenReturn(listOf(aComparisonPerson(1, "A1234BC")))
    whenever(comparisonPersonRepository.findByComparisonId(2)).thenReturn(listOf(aComparisonPerson(2, "A1234BC")))
    whenever(comparisonPersonRepository.findByComparisonId(3)).thenReturn(listOf(aComparisonPerson(3, "A1234BC")))
    whenever(comparisonPersonRepository.findByComparisonId(4)).thenReturn(listOf(aComparisonPerson(4, "A1234BC")))

    doNothing().whenever(deleteCalculationService).deletePreliminaryOrBulk(101)
    doNothing().whenever(deleteCalculationService).deletePreliminaryOrBulk(102)
    doNothing().whenever(deleteCalculationService).deletePreliminaryOrBulk(103)
    doNothing().whenever(deleteCalculationService).deletePreliminaryOrBulk(104)
    doNothing().whenever(comparisonRepository).deleteById(1)
    doNothing().whenever(comparisonRepository).deleteById(2)
    doNothing().whenever(comparisonRepository).deleteById(3)
    doNothing().whenever(comparisonRepository).deleteById(4)

    val result = service.deleteBatch(100)

    assertThat(result).isEqualTo(DeletedBulkComparisonsResult(120, 3))

    verify(deleteCalculationService, never()).deletePreliminaryOrBulk(101)
    verify(deleteCalculationService).deletePreliminaryOrBulk(102)
    verify(deleteCalculationService).deletePreliminaryOrBulk(103)
    verify(deleteCalculationService).deletePreliminaryOrBulk(104)
    verify(comparisonRepository, never()).deleteById(1)
    verify(comparisonRepository).deleteById(2)
    verify(comparisonRepository).deleteById(3)
    verify(comparisonRepository).deleteById(4)
  }

  @Test
  fun `should still delete a calc even if the oldest is bigger than the batch size`() {
    val comparisonToDelete1 = ComparisonToDelete(1, 40, LocalDateTime.now().minusDays(20).toInstant(ZoneOffset.UTC))
    val comparisonToDelete2 = ComparisonToDelete(2, 40, LocalDateTime.now().minusDays(21).toInstant(ZoneOffset.UTC))
    val comparisonToDelete3 = ComparisonToDelete(3, 40, LocalDateTime.now().minusDays(22).toInstant(ZoneOffset.UTC))
    val comparisonToDelete4 = ComparisonToDelete(4, 500, LocalDateTime.now().minusDays(23).toInstant(ZoneOffset.UTC))

    whenever(comparisonRepository.allComparisonsOlderThanDateExceptTheLatestTwo(any())).thenReturn(listOf(comparisonToDelete1, comparisonToDelete2, comparisonToDelete3, comparisonToDelete4))
    whenever(comparisonPersonRepository.findByComparisonId(4)).thenReturn(listOf(aComparisonPerson(4, "A1234BC")))

    doNothing().whenever(deleteCalculationService).deletePreliminaryOrBulk(104)
    doNothing().whenever(comparisonRepository).deleteById(4)

    val result = service.deleteBatch(100)

    assertThat(result).isEqualTo(DeletedBulkComparisonsResult(500, 1))

    verify(deleteCalculationService, never()).deletePreliminaryOrBulk(101)
    verify(deleteCalculationService, never()).deletePreliminaryOrBulk(102)
    verify(deleteCalculationService, never()).deletePreliminaryOrBulk(103)
    verify(deleteCalculationService).deletePreliminaryOrBulk(104)
    verify(comparisonRepository, never()).deleteById(1)
    verify(comparisonRepository, never()).deleteById(2)
    verify(comparisonRepository, never()).deleteById(3)
    verify(comparisonRepository).deleteById(4)
  }

  private fun aComparisonPerson(
    comparisonId: Long,
    person: String,
  ): ComparisonPerson {
    val emptyObjectNode = objectMapper.createObjectNode()
    return ComparisonPerson(
      1,
      comparisonId,
      person = person,
      lastName = "Smith",
      latestBookingId = 25,
      isMatch = false,
      isValid = true,
      isFatal = false,
      mismatchType = MismatchType.RELEASE_DATES_MISMATCH,
      validationMessages = emptyObjectNode,
      calculatedByUsername = USERNAME,
      nomisDates = emptyObjectNode,
      overrideDates = emptyObjectNode,
      breakdownByReleaseDateType = emptyObjectNode,
      sdsPlusSentencesIdentified = emptyObjectNode,
      establishment = "BMI",
      hasIndeterminateSentences = false,
      calculationRequestId = comparisonId + 100,
    )
  }
}
