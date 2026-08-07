package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ProgressionModelExclusionResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AdjustmentsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceAndOffenceService

class SourceDataProxyControllerTest {

  val sentenceAndOffenceService: SentenceAndOffenceService = mock()
  val adjustmentsService: AdjustmentsService = mock()
  val featureToggles = FeatureToggles()
  private val controller = SourceDataProxyController(sentenceAndOffenceService, adjustmentsService, featureToggles)

  @Test
  fun `should return the progression model exclusion for a prisoner if the feature toggle is on`() {
    featureToggles.progressionModelScheduleExclusionEnabled = true
    whenever(sentenceAndOffenceService.hasOffencesExcludedFromProgressionModelNotIncludingSchedule13Part3("A1234BC")).thenReturn(true)
    val response = controller.hasOffencesExcludedFromProgressionModel("A1234BC")
    assertThat(response).isEqualTo(ProgressionModelExclusionResponse(true))
  }

  @Test
  fun `should not return the progression model exclusion for a prisoner if the feature toggle is off`() {
    featureToggles.progressionModelScheduleExclusionEnabled = false
    val exception = assertThrows<CrdWebException> {
      controller.hasOffencesExcludedFromProgressionModel("A1234BC")
    }
    assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
  }
}
