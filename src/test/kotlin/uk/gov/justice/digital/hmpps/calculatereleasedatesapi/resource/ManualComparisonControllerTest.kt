package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDiscrepancySummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManualComparisonService
import java.time.LocalDateTime
import java.util.UUID

@ActiveProfiles("test")
@WebMvcTest(controllers = [ManualComparisonController::class])
class ManualComparisonControllerTest {

  @MockitoBean
  private lateinit var manualComparisonService: ManualComparisonService

  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val jackson2HttpMessageConverter = JacksonJsonHttpMessageConverter()

  @BeforeEach
  fun reset() {
    Mockito.reset(manualComparisonService)

    mvc = MockMvcBuilders
      .standaloneSetup(ManualComparisonController(manualComparisonService, objectMapper))
      .setControllerAdvice(ControllerAdvice())
      .setMessageConverters(this.jackson2HttpMessageConverter)
      .build()
  }

  @Test
  fun `Test POST for creation of comparison`() {
    val comparisonInput = ManualComparisonInput(listOf("ABC123"))

    whenever(manualComparisonService.create(comparisonInput, "Bearer token")).thenReturn(
      Comparison(
        1, UUID.randomUUID(), "ABCD1234", objectMapper.createObjectNode(), "JAS", ComparisonType.MANUAL, LocalDateTime.now(), "JOEL",
        ComparisonStatus.PROCESSING,
      ),
    )

    val result = mvc.perform(
      MockMvcRequestBuilders
        .post("/comparison/manual")
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
  fun `Test GET of preconfigured comparisons`() {
    val startTime = LocalDateTime.now().minusHours(2)
    whenever(manualComparisonService.listManual()).thenReturn(listOf(ComparisonSummary("ABCD1234", null, ComparisonType.MANUAL, ComparisonStatus.PROCESSING, startTime, "JOEL", 0, 10, 5, 0)))

    val result = mvc.perform(
      MockMvcRequestBuilders.get("/comparison/manual")
        .accept(MediaType.APPLICATION_JSON),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).contains("\"comparisonShortReference\":\"ABCD1234\"")
    assertThat(result.response.contentAsString).contains("\"percentageComplete\":50.0")
    // Started two hours ago, 50% complete. ETA is in 4 hours from start.
    assertThat(result.response.contentAsString).contains("\"expectedCompletionTime\":\"${startTime.plusHours(4).toString().take(16)}")
  }

  @Test
  fun `Test get comparison person discrepancy`() {
    val comparisonReference = "ABCD1234"
    val comparisonPersonReference = "DFADSE4343"
    val summary = ComparisonDiscrepancySummary(DiscrepancyImpact.POTENTIAL_UNLAWFUL_DETENTION, emptyList(), "detail", DiscrepancyPriority.HIGH_RISK, "action")
    whenever(manualComparisonService.getComparisonPersonDiscrepancy(comparisonReference, comparisonPersonReference)).thenReturn(summary)

    val result = mvc.perform(
      MockMvcRequestBuilders.get("/comparison/manual/$comparisonReference/mismatch/$comparisonPersonReference/discrepancy")
        .accept(MediaType.APPLICATION_JSON),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo("""{"impact":"POTENTIAL_UNLAWFUL_DETENTION","causes":[],"detail":"detail","priority":"HIGH_RISK","action":"action"}""")
  }

  @Test
  fun `Test creation of comparison person discrepancy`() {
    val comparisonReference = "ABCD1234"
    val comparisonPersonReference = "DFADSE4343"
    val request = CreateComparisonDiscrepancyRequest(DiscrepancyImpact.POTENTIAL_UNLAWFUL_DETENTION, emptyList(), "detail", DiscrepancyPriority.HIGH_RISK, "action")
    val summary = ComparisonDiscrepancySummary(DiscrepancyImpact.POTENTIAL_UNLAWFUL_DETENTION, emptyList(), "detail", DiscrepancyPriority.HIGH_RISK, "action")
    whenever(manualComparisonService.createDiscrepancy(comparisonReference, comparisonPersonReference, request)).thenReturn(summary)

    val result = mvc.perform(
      MockMvcRequestBuilders
        .post("/comparison/manual/$comparisonReference/mismatch/$comparisonPersonReference/discrepancy")
        .header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andReturn()

    assertThat(result.response.contentAsString).contains("""{"impact":"POTENTIAL_UNLAWFUL_DETENTION","causes":[],"detail":"detail","priority":"HIGH_RISK","action":"action"}""")
  }
}
