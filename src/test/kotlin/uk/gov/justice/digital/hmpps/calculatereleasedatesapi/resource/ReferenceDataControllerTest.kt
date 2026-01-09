package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.common.util.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DateTypeDefinition
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@WebMvcTest(controllers = [ReferenceDataController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = [ReferenceDataController::class])
@WebAppConfiguration
class ReferenceDataControllerTest {

  @Autowired
  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Test
  fun `should be able to get date types`() {
    val result = mvc.perform(
      MockMvcRequestBuilders.get("/reference-data/date-type")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON),
    )
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    val readValue = mapper.readValue(result.response.contentAsString, object : TypeReference<List<DateTypeDefinition>>() {})
    assertThat(readValue).hasSize(ReleaseDateType.entries.size)
    assertThat(readValue).describedAs("all have a description").allMatch { StringUtils.isNotBlank(it.description) }
  }
}
