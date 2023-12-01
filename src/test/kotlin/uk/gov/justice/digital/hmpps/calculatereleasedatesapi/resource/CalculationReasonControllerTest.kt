package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository

@ActiveProfiles("test")
@WebMvcTest(controllers = [CalculationReasonController::class])
class CalculationReasonControllerTest {

  @MockBean
  private lateinit var calculationReasonRepository: CalculationReasonRepository

  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Autowired
  private lateinit var jackson2HttpMessageConverter: MappingJackson2HttpMessageConverter

  @Test
  fun `Test GET of the active calculation reasons`() {
    mvc = MockMvcBuilders
      .standaloneSetup(CalculationReasonController(calculationReasonRepository))
      .setControllerAdvice(ControllerAdvice())
      .setMessageConverters(this.jackson2HttpMessageConverter)
      .build()

    val calculationReasons = listOf(
      CalculationReason(1, isActive = true, isOther = false, displayName = "Reason 1"),
      CalculationReason(2, isActive = true, isOther = false, displayName = "Reason 2"),
      CalculationReason(3, isActive = true, isOther = true, displayName = "Other"),
    )

    whenever(calculationReasonRepository.findAllByIsActiveTrueOrderById()).thenReturn(calculationReasons)

    val result = mvc.perform(get("/calculation-reasons/").accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    val returnedJson = result.response.contentAsString
    assertEquals(mapper.writeValueAsString(calculationReasons), returnedJson)
    assertFalse(returnedJson.contains("\"isActive\":"), "Active tags are not required as they will all be true")
  }
}
