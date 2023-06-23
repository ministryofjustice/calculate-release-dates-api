package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
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
    val comparisonInput = ComparisonInput(objectMapper.createObjectNode(), false, "ABC")

    whenever(comparisonService.create(comparisonInput)).thenReturn(Comparison(1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), "JAS", false, LocalDateTime.now(), "JOEL"))

    val result = mvc.perform(
      MockMvcRequestBuilders
        .post("/compare/")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(comparisonInput)),
    )
      .andDo(MockMvcResultHandlers.print())
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andReturn()

    Assertions.assertThat(result.response.contentAsString).contains("ABCD1234")
  }

  @Test
  fun `Test GET of manual input comparisons`() {
    whenever(comparisonService.listManual()).thenReturn(listOf(Comparison(1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), null, true, LocalDateTime.now(), "JOEL")))

    val result = mvc.perform(
      MockMvcRequestBuilders.get("/compare/list/manual")
        .accept(MediaType.APPLICATION_JSON),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    Assertions.assertThat(result.response.contentAsString).contains("\"comparisonShortReference\":\"ABCD1234\"")
  }

  @Test
  fun `Test GET of preconfigured comparisons`() {
    whenever(comparisonService.listComparisons()).thenReturn(listOf(Comparison(1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), "JAS", false, LocalDateTime.now(), "JOEL")))

    val result = mvc.perform(
      MockMvcRequestBuilders.get("/compare/list/")
        .accept(MediaType.APPLICATION_JSON),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    Assertions.assertThat(result.response.contentAsString).contains("\"comparisonShortReference\":\"ABCD1234\"")
  }

  @Test
  fun `Test GET of count of person in a comparison`() {
    val shortReference = "ABCD1234"
    whenever(comparisonService.getCountOfPersonsInComparisonByComparisonReference(shortReference)).thenReturn(7)

    val result = mvc.perform(
      MockMvcRequestBuilders.get("/compare/$shortReference/count/")
        .accept(MediaType.APPLICATION_JSON),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    Assertions.assertThat(result.response.contentAsString).isEqualTo("7")
  }
}
