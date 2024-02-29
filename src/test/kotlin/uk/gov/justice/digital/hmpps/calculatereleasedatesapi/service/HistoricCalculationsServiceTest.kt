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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
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
    whenever(prisonApiClient.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary("comment $reference")))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(1)
    assertThat(result[0].calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result[0].calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(reference.toString(), 1))
  }

  @Test
  fun `Test source set to NOMIS if calculation not found in database`() {
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calculationRequest()))
    whenever(prisonApiClient.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary("comment")))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(1)
    assertThat(result[0].calculationSource).isEqualTo(CalculationSource.NOMIS)
    assertThat(result[0].calculationViewConfiguration).isNull()
  }

  private fun sentenceCalculationSummary(comment: String): SentenceCalculationSummary {
    return SentenceCalculationSummary(456, "123", "bob", "davies", "RNI", "Ranby (HMP)", 1, LocalDateTime.now(), 4, comment, "reason", "user")
  }

  private fun calculationRequest(): CalculationRequest {
    return CalculationRequest(1, reference, "123", 4565, CalculationStatus.CONFIRMED.name, calculatedAt = LocalDateTime.now())
  }
}
