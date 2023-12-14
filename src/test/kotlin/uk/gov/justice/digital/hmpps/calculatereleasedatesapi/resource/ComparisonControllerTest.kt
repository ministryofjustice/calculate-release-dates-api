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
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ComparisonService
import java.time.LocalDateTime
import java.util.UUID

@ActiveProfiles("test")
@WebMvcTest(controllers = [ComparisonController::class])
class ComparisonControllerTest {

  @MockBean
  private lateinit var comparisonService: ComparisonService

  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var jackson2HttpMessageConverter: MappingJackson2HttpMessageConverter

  @BeforeEach
  fun reset() {
    Mockito.reset(comparisonService)

    mvc = MockMvcBuilders
      .standaloneSetup(ComparisonController(comparisonService))
      .setControllerAdvice(ControllerAdvice())
      .setMessageConverters(this.jackson2HttpMessageConverter)
      .build()
  }

  @Test
  fun `Test POST for creation of comparison`() {
    val comparisonInput = ComparisonInput(objectMapper.createObjectNode(), "ABC")

    whenever(comparisonService.create(comparisonInput)).thenReturn(
      Comparison(
        1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), "JAS", false, ComparisonType.ESTABLISHMENT_FULL, LocalDateTime.now(), "JOEL",
        ComparisonStatus(ComparisonStatusValue.PROCESSING),
      ),
    )

    val result = mvc.perform(
      MockMvcRequestBuilders
        .post("/comparison/")
        .header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(comparisonInput)),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andReturn()

    assertThat(result.response.contentAsString).contains("ABCD1234")
  }

  @Test
  fun `Test GET of manual input comparisons`() {
    val comparisonList = listOf(ComparisonSummary("ABCD1234", null, ComparisonType.ESTABLISHMENT_FULL, LocalDateTime.now(), "JOEL", 0, 0))
    whenever(comparisonService.listComparisons()).thenReturn(comparisonList)

    val result = mvc.perform(
      MockMvcRequestBuilders.get("/comparison/")
        .accept(MediaType.APPLICATION_JSON),
    )
      .andDo(MockMvcResultHandlers.print())
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).contains("\"comparisonShortReference\":\"ABCD1234\"")
  }

  @Test
  fun `Test GET of count of person in a comparison`() {
    val shortReference = "ABCD1234"
    whenever(comparisonService.getCountOfPersonsInComparisonByComparisonReference(shortReference)).thenReturn(7)

    val result = mvc.perform(
      MockMvcRequestBuilders.get("/comparison/$shortReference/count/")
        .accept(MediaType.APPLICATION_JSON),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo("7")
  }
}
