package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import java.util.UUID

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@WebMvcTest(controllers = [CalculationReferenceController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = [CalculationReferenceController::class])
@WebAppConfiguration
class CalculationReferenceControllerTest {

  @MockBean
  private lateinit var calculationTransactionalService: CalculationTransactionalService

  @Autowired
  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  @BeforeEach
  fun reset() {
    Mockito.reset(calculationTransactionalService)

    mvc = MockMvcBuilders
      .standaloneSetup(CalculationReferenceController(calculationTransactionalService))
      .setControllerAdvice(ControllerAdvice())
      .build()
  }

  @Test
  fun `Test GET of calculation results by calculationReference`() {
    val calculationRequestId = 9995L
    val calculationReference = UUID.randomUUID()
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = calculationRequestId,
      dates = mapOf(),
      calculationStatus = CalculationStatus.PRELIMINARY,
      bookingId = 123L,
      prisonerId = "ASD",
      calculationReference = UUID.randomUUID(),
      calculationReason = CalculationReason(-1, true, false, "Reason", false),
    )

    whenever(calculationTransactionalService.findCalculationResultsByCalculationReference(calculationReference.toString())).thenReturn(calculatedReleaseDates)

    val result = mvc.perform(MockMvcRequestBuilders.get("/calculationReference/$calculationReference").accept(MediaType.APPLICATION_JSON))
      .andExpect(MockMvcResultMatchers.status().isOk)
      .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
      .andReturn()

    Assertions.assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(calculatedReleaseDates))
    verify(calculationTransactionalService, times(1)).findCalculationResultsByCalculationReference(eq(calculationReference.toString()), eq(false))
  }
}
