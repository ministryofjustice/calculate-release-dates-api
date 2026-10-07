package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ProgressionModelExclusionResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AdjustmentsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceAndOffenceService

class SourceDataProxyControllerTest {

  val sentenceAndOffenceService: SentenceAndOffenceService = mock()
  val adjustmentsService: AdjustmentsService = mock()
  private val controller = SourceDataProxyController(sentenceAndOffenceService, adjustmentsService)

  @Test
  fun `should return the progression model exclusion for a prisoner`() {
    whenever(sentenceAndOffenceService.hasOffencesExcludedFromProgressionModelNotIncludingSchedule13Part3("A1234BC")).thenReturn(true)
    val response = controller.hasOffencesExcludedFromProgressionModel("A1234BC")
    assertThat(response).isEqualTo(ProgressionModelExclusionResponse(true))
  }
}
