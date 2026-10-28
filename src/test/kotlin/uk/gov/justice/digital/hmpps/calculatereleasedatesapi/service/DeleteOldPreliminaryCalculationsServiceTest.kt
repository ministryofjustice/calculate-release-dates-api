package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DeletedPreviousCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

class DeleteOldPreliminaryCalculationsServiceTest {

  private val calculationRequestRepository: CalculationRequestRepository = mock()
  private val deleteCalculationService: DeleteCalculationService = mock()

  private val service = DeleteOldPreliminaryCalculationsService(calculationRequestRepository, deleteCalculationService)

  @Test
  fun `should handle failures in a single deletion gracefully`() {
    whenever(calculationRequestRepository.findPreliminaryCalculationRequestIdsOlderThan(any(), any())).thenReturn(listOf(1, 2, 3))
    doNothing().whenever(deleteCalculationService).deletePreliminaryOrBulk(1)
    whenever(deleteCalculationService.deletePreliminaryOrBulk(2)).thenThrow(EntityNotFoundException("Bang!"))
    doNothing().whenever(deleteCalculationService).deletePreliminaryOrBulk(3)

    val response = service.deleteBatch(2, 1000)

    assertThat(response).isEqualTo(DeletedPreviousCalculationResult(3, 2))

    verify(deleteCalculationService).deletePreliminaryOrBulk(1)
    verify(deleteCalculationService).deletePreliminaryOrBulk(2)
    verify(deleteCalculationService).deletePreliminaryOrBulk(3)
  }
}
