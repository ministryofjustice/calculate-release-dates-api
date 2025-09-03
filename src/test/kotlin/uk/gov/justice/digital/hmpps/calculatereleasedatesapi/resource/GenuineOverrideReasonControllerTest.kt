package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice

@ActiveProfiles("test")
@WebMvcTest(controllers = [GenuineOverrideReasonController::class])
class GenuineOverrideReasonControllerTest {

  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var jackson2HttpMessageConverter: MappingJackson2HttpMessageConverter

  @Test
  fun `Test GET of the genuine override reasons`() {
    mvc = MockMvcBuilders
      .standaloneSetup(GenuineOverrideReasonController())
      .setControllerAdvice(ControllerAdvice())
      .setMessageConverters(this.jackson2HttpMessageConverter)
      .build()

    mvc.perform(get("/genuine-override-reasons/").accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(
        jsonPath(
          "$[*].code",
          containsInAnyOrder(
            // avoid renaming the codes as they will be stored against genuine overrides.
            "RECORD_CROSS_BORDER_SECTION_RELEASE_DATES",
            "RECORD_POWER_TO_DETAIN",
            "RECORD_ERS_BREACH",
            "ADD_RELEASE_DATES_FROM_PREVIOUS_BOOKING",
            "MISALIGNED_COURT_DOCUMENTS",
            "UNSUPPORTED_SENTENCES",
            "OTHER",
          ),
        ),
      )
  }
}
