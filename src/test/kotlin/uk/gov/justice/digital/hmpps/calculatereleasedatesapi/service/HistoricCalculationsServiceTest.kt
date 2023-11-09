package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationViewConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class HistoricCalculationsServiceTest {
  @Mock
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Mock
  lateinit var prisonApiClient: PrisonApiClient

  @InjectMocks
  lateinit var underTest: HistoricCalculationsService
  val reference: UUID = UUID.randomUUID()

  @Test
  fun `Test source set to CRDS if calculation found in database`() {
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calculationRequest()))
    whenever(prisonApiClient.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(offenderSentenceCalculation("comment $reference")))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(1)
    assertThat(result.get(0).calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result.get(0).calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(reference.toString(), 1))
  }

  @Test
  fun `Test source set to NOMIS if calculation not found in database`() {
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calculationRequest()))
    whenever(prisonApiClient.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(offenderSentenceCalculation("comment")))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(1)
    assertThat(result.get(0).calculationSource).isEqualTo(CalculationSource.NOMIS)
    assertThat(result.get(0).calculationViewConfiguration).isNull()
  }

  private fun offenderSentenceCalculation(comment: String): OffenderSentenceCalculation {
    return OffenderSentenceCalculation(456, "123", "test", "prison", "KMI", 1, LocalDateTime.now(), commentText = comment)
  }

  private fun calculationRequest(): CalculationRequest {
    return CalculationRequest(1, reference, "123", 4565, CalculationStatus.CONFIRMED.name, calculatedAt = LocalDateTime.now())
  }
}
