package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationViewConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.HistoricCalculationsService
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@WebMvcTest(controllers = [LegacyHistoricCalculationsController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = [LegacyHistoricCalculationsController::class])
@WebAppConfiguration
class LegacyHistoricCalculationsControllerTest {

  @MockitoBean
  private lateinit var historicCalculationsService: HistoricCalculationsService

  @Autowired
  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  @BeforeEach
  fun reset() {
    reset(historicCalculationsService)

    mvc = MockMvcBuilders
      .standaloneSetup(LegacyHistoricCalculationsController(historicCalculationsService))
      .setControllerAdvice(ControllerAdvice())
      .build()
    mapper.findAndRegisterModules()
  }

  @Test
  fun `Test GET of calculation history`() {
    val historicCalculation = HistoricCalculation(
      "A1234BC",
      LocalDateTime.now(),
      CalculationSource.CRDS,
      CalculationViewConfiguration("ref", 1),
      "Comment",
      CalculationType.CALCULATED,
      "Ranby (HMP)",
      48,
      "Adding more sentences or terms",
      null,
      -1,
      genuineOverrideReasonCode = null,
      genuineOverrideReasonDescription = null,
      calculatedByUsername = "user1",
      calculatedByDisplayName = "User One",
      secondCheckDetails = emptyList(),
    )

    whenever(historicCalculationsService.getHistoricCalculationsForPrisoner(anyString())).thenReturn(listOf(historicCalculation))

    val result = mvc.perform(MockMvcRequestBuilders.get("/historicCalculations/A1234BC").accept(MediaType.APPLICATION_JSON))
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(listOf(historicCalculation)))
    verify(historicCalculationsService).getHistoricCalculationsForPrisoner(eq("A1234BC"))
  }
}
