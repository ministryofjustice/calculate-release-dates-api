package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManualComparisonService
import java.time.LocalDateTime
import java.util.UUID

@ActiveProfiles("test")
@WebMvcTest(controllers = [ManualComparisonController::class])
class ManualComparisonControllerTest {

  @MockBean
  private lateinit var manualComparisonService: ManualComparisonService

  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var jackson2HttpMessageConverter: MappingJackson2HttpMessageConverter

  @BeforeEach
  fun reset() {
    Mockito.reset(manualComparisonService)

    mvc = MockMvcBuilders
      .standaloneSetup(ManualComparisonController(manualComparisonService))
      .setControllerAdvice(ControllerAdvice())
      .setMessageConverters(this.jackson2HttpMessageConverter)
      .build()
  }

  @Test
  fun `Test POST for creation of comparison`() {
    val comparisonInput = ManualComparisonInput(listOf("ABC123"))

    whenever(manualComparisonService.create(comparisonInput)).thenReturn(
      Comparison(
        1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), "JAS", false, LocalDateTime.now(), "JOEL",
        ComparisonStatus(ComparisonStatusValue.PROCESSING),
      ),
    )

    val result = mvc.perform(
      MockMvcRequestBuilders
        .post("/comparison/manual/")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(comparisonInput)),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andReturn()

    assertThat(result.response.contentAsString).contains("ABCD1234")
  }

  @Test
  fun `Test GET of preconfigured comparisons`() {
    whenever(manualComparisonService.listManual()).thenReturn(listOf(ComparisonSummary("ABCD1234", null, LocalDateTime.now(), "JOEL", 0, 0)))

    val result = mvc.perform(
      MockMvcRequestBuilders.get("/comparison/manual/")
        .accept(MediaType.APPLICATION_JSON),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).contains("\"comparisonShortReference\":\"ABCD1234\"")
  }
}
