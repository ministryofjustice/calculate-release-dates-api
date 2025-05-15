package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.WorkingDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.WorkingDayService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ActiveProfiles("test")
@WebMvcTest(controllers = [WorkingDayController::class])
class WorkingDayControllerTest {

  @MockBean
  private lateinit var workingDayService: WorkingDayService

  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Autowired
  private lateinit var jackson2HttpMessageConverter: MappingJackson2HttpMessageConverter

  @BeforeEach
  fun reset() {
    reset(workingDayService)

    mvc = MockMvcBuilders
      .standaloneSetup(WorkingDayController(workingDayService))
      .setControllerAdvice(ControllerAdvice())
      .setMessageConverters(this.jackson2HttpMessageConverter)
      .build()
  }

  @Test
  fun `Test GET of a next working day`() {
    val saturday = LocalDate.of(2021, 10, 30)
    val monday = LocalDate.of(2021, 11, 1)
    val workingDay = WorkingDay(monday, adjustedForWeekend = true)
    whenever(workingDayService.nextWorkingDay(saturday)).thenReturn(workingDay)

    val result = mvc.perform(get("/working-day/next/${dateFormat(saturday)}").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(workingDay))
  }

  @Test
  fun `Test GET of a previous working day`() {
    val saturday = LocalDate.of(2021, 10, 30)
    val friday = LocalDate.of(2021, 10, 29)
    val workingDay = WorkingDay(friday, adjustedForWeekend = true)
    whenever(workingDayService.previousWorkingDay(saturday)).thenReturn(workingDay)

    val result = mvc.perform(get("/working-day/previous/${dateFormat(saturday)}").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(workingDay))
  }

  private fun dateFormat(date: LocalDate): String = date.format(DateTimeFormatter.ISO_DATE)
}
