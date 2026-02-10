package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.GenuineOverrideService

@ActiveProfiles("test")
@WebMvcTest(controllers = [GenuineOverrideController::class])
class GenuineOverrideReasonControllerTest {

  private lateinit var mvc: MockMvc

  private val jackson2HttpMessageConverter = JacksonJsonHttpMessageConverter()

  @MockitoBean
  private lateinit var genuineOverrideService: GenuineOverrideService

  @Test
  fun `Test GET of the genuine override reasons`() {
    mvc = MockMvcBuilders
      .standaloneSetup(GenuineOverrideController(genuineOverrideService))
      .setControllerAdvice(ControllerAdvice())
      .setMessageConverters(this.jackson2HttpMessageConverter)
      .build()

    mvc.perform(get("/genuine-override/reasons").accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(
        jsonPath(
          "$[*].code",
          containsInAnyOrder(
            // avoid renaming the codes as they will be stored against genuine overrides.
            "TRIAL_RECORD_OR_BREAKDOWN_DOES_NOT_MATCH_OVERALL_SENTENCE_LENGTH",
            "RELEASE_DATE_FROM_ANOTHER_CUSTODY_PERIOD",
            "POWER_TO_DETAIN",
            "ENTER_APPROVED_DATES",
            "RELEASE_DATE_ON_WEEKEND_OR_HOLIDAY",
            "CROSS_BORDER_SECTION_RELEASE_DATE",
            "AGGRAVATING_FACTOR_OFFENCE",
            "OTHER",
          ),
        ),
      )
  }
}
