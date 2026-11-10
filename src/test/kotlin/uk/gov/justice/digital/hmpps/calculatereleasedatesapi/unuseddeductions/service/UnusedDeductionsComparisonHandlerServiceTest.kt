package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UnusedDeductionCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AdjustmentsApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BulkComparisonMessageBody
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.InternalMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonRepository
import java.util.Optional

class UnusedDeductionsComparisonHandlerServiceTest {
  private val comparisonRepository: UnusedDeductionsComparisonRepository = mock()
  private val comparisonPersonRepository: UnusedDeductionsComparisonPersonRepository = mock()
  private val adjustmentsApiClient: AdjustmentsApiClient = mock()
  private val calculationService: UnusedDeductionsCalculationService = mock()
  private val objectMapper = TestUtil.objectMapper()

  private val service = UnusedDeductionsComparisonHandlerService(comparisonRepository, comparisonPersonRepository, adjustmentsApiClient, calculationService, objectMapper)

  private val comparison = UnusedDeductionsComparison(
    id = 1,
    criteria = objectMapper.createObjectNode(),
    prison = "MDI",
    comparisonType = UnusedDeductionsComparisonType.PRISON,
    calculatedByUsername = "FOO",
    comparisonStatus = ComparisonStatus.PROCESSING,
    numberOfPeopleExpected = 1,
  )
  private val message = InternalMessage(BulkComparisonMessageBody(1, "A1234BC", "FOO", null))

  private fun adjustment(type: AdjustmentDto.AdjustmentType, days: Int, effectiveDays: Int) = AdjustmentDto(person = "A1234BC", adjustmentType = type, days = days, effectiveDays = effectiveDays)

  private fun detail(unused: Long?) = UnusedDeductionsCalculationDetail(UnusedDeductionCalculationResponse(unused), 122, 183, PrisonerDetails(bookingId = 123, offenderNo = "A1234BC", dateOfBirth = java.time.LocalDate.of(1990, 1, 1), lastName = "SMITH"))

  private fun saved(): UnusedDeductionsComparisonPerson {
    val captor = argumentCaptor<UnusedDeductionsComparisonPerson>()
    verify(comparisonPersonRepository).save(captor.capture())
    return captor.firstValue
  }

  private fun givenAdjustments(vararg adjustments: AdjustmentDto) {
    whenever(comparisonRepository.findById(1)).thenReturn(Optional.of(comparison))
    whenever(comparisonPersonRepository.findByComparisonIdAndPerson(1, "A1234BC")).thenReturn(emptyList())
    whenever(adjustmentsApiClient.getAdjustmentsByPerson("A1234BC", listOf(AdjustmentDto.Status.ACTIVE), true)).thenReturn(adjustments.toList())
  }

  @Test
  fun `matching result stores the calculation inputs and intermediate values`() {
    givenAdjustments(adjustment(AdjustmentDto.AdjustmentType.REMAND, 183, 122), adjustment(AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS, 61, 61))
    whenever(calculationService.calculateDetailed(any(), any())).thenReturn(detail(61))

    service.handleMessage(message)

    val person = saved()
    assertThat(person.outcome).isEqualTo(UnusedDeductionsComparisonOutcome.MATCH)
    assertThat(person.existingUnusedDeductions).isEqualTo(61)
    assertThat(person.calculatedUnusedDeductions).isEqualTo(61)
    assertThat(person.custodialDays).isEqualTo(122)
    assertThat(person.totalDeductions).isEqualTo(183)
    assertThat(person.establishment).isEqualTo("MDI")
    assertThat(person.adjustments).hasSize(2)
    assertThat(person.adjustments[0]["days"].asInt()).isEqualTo(183)
    assertThat(person.adjustments[0]["effectiveDays"].asInt()).isEqualTo(122)
  }

  @Test
  fun `no existing adjustment is compared as zero`() {
    givenAdjustments(adjustment(AdjustmentDto.AdjustmentType.REMAND, 183, 122))
    whenever(calculationService.calculateDetailed(any(), any())).thenReturn(detail(61))

    service.handleMessage(message)

    val person = saved()
    assertThat(person.outcome).isEqualTo(UnusedDeductionsComparisonOutcome.MISMATCH)
    assertThat(person.existingUnusedDeductions).isEqualTo(0)
  }

  @Test
  fun `validation failures are recorded`() {
    givenAdjustments()
    whenever(calculationService.calculateDetailed(any(), any())).thenReturn(detail(null))

    service.handleMessage(message)

    assertThat(saved().outcome).isEqualTo(UnusedDeductionsComparisonOutcome.VALIDATION_ERROR)
  }

  @Test
  fun `exceptions are recorded as fatal errors`() {
    givenAdjustments(adjustment(AdjustmentDto.AdjustmentType.REMAND, 183, 122))
    whenever(calculationService.calculateDetailed(any(), any())).thenThrow(RuntimeException("boom"))

    service.handleMessage(message)

    val person = saved()
    assertThat(person.outcome).isEqualTo(UnusedDeductionsComparisonOutcome.FATAL_ERROR)
    assertThat(person.fatalException).isEqualTo("boom")
    assertThat(person.adjustments).hasSize(1)
  }

  @Test
  fun `already processed people are skipped on retry`() {
    whenever(comparisonRepository.findById(1)).thenReturn(Optional.of(comparison))
    whenever(comparisonPersonRepository.findByComparisonIdAndPerson(1, "A1234BC")).thenReturn(listOf(mock()))

    service.handleMessage(message)

    verify(comparisonPersonRepository, never()).save(any<UnusedDeductionsComparisonPerson>())
  }

  @Test
  fun `comparison completes when everyone has been processed`() {
    whenever(comparisonRepository.findById(1)).thenReturn(Optional.of(comparison))
    whenever(comparisonPersonRepository.countByComparisonId(1)).thenReturn(1)
    whenever(comparisonPersonRepository.countByComparisonIdAndOutcomeIn(1, listOf(UnusedDeductionsComparisonOutcome.FATAL_ERROR))).thenReturn(0)

    service.updateCountsAndCheckIfComparisonIsComplete(1)

    assertThat(comparison.comparisonStatus).isEqualTo(ComparisonStatus.COMPLETED)
    assertThat(comparison.numberOfPeopleCompared).isEqualTo(1)
  }
}
