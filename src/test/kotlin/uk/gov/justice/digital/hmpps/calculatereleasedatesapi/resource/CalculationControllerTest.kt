package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ControllerAdvice
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationUserQuestionService

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@WebMvcTest(controllers = [CalculationController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = [CalculationController::class])
@WebAppConfiguration
class CalculationControllerTest {
  @MockBean
  private lateinit var calculationTransactionalService: CalculationTransactionalService

  @MockBean
  private lateinit var calculationUserQuestionService: CalculationUserQuestionService

  @Autowired
  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  private val calculationFragments = CalculationFragments("<p>breakdown</p>")

  @BeforeEach
  fun reset() {
    reset(calculationTransactionalService)

    mvc = MockMvcBuilders
      .standaloneSetup(CalculationController(calculationTransactionalService, calculationUserQuestionService))
      .setControllerAdvice(ControllerAdvice())
      .build()
  }

  @Test
  fun `Test POST of a PRELIMINARY calculation`() {
    val prisonerId = "A1234AB"
    val bookingId = 9995L

    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L, dates = mapOf(), calculationStatus = PRELIMINARY,
      bookingId = bookingId, prisonerId = prisonerId
    )
    whenever(calculationTransactionalService.calculate(prisonerId, CalculationUserInputs())).thenReturn(calculatedReleaseDates)

    val result = mvc.perform(post("/calculation/$prisonerId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(calculatedReleaseDates))
    verify(calculationTransactionalService, times(1)).calculate(prisonerId, CalculationUserInputs())
  }

  @Test
  fun `Test POST of a PRELIMINARY calculation with user input`() {
    val prisonerId = "A1234AB"
    val bookingId = 9995L
    val userInput = CalculationUserInputs(listOf(CalculationSentenceUserInput(1, "ABC", UserInputType.ORIGINAL, true)))
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L, dates = mapOf(), calculationStatus = PRELIMINARY,
      bookingId = bookingId, prisonerId = prisonerId
    )
    whenever(calculationTransactionalService.calculate(prisonerId, userInput)).thenReturn(calculatedReleaseDates)

    val result = mvc.perform(
      post("/calculation/$prisonerId")
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content(mapper.writeValueAsString(userInput))
    )
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(calculatedReleaseDates))
    verify(calculationTransactionalService, times(1)).calculate(prisonerId, userInput)
  }

  @Test
  fun `Test POST to confirm a calculation and that event is published`() {
    val prisonerId = "A1234AB"
    val calculationRequestId = 12345L
    val bookingId = 9995L

    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L, dates = mapOf(), calculationStatus = PRELIMINARY,
      bookingId = bookingId, prisonerId = prisonerId
    )
    whenever(calculationTransactionalService.validateAndConfirmCalculation(calculationRequestId, calculationFragments)).thenReturn(calculatedReleaseDates)

    val result = mvc.perform(
      post("/calculation/confirm/$calculationRequestId")
        .accept(APPLICATION_JSON)
        .content(mapper.writeValueAsString(calculationFragments))
        .contentType(APPLICATION_JSON)
    )
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(calculatedReleaseDates))
    verify(calculationTransactionalService, times(1)).validateAndConfirmCalculation(calculationRequestId, calculationFragments)
  }

  @Test
  fun `Test POST to confirm a calculation when the data has changed since the PRELIM calc - results in exception`() {
    val calculationRequestId = 12345L

    whenever(calculationTransactionalService.validateAndConfirmCalculation(calculationRequestId, calculationFragments)).then {
      throw PreconditionFailedException(
        "The booking data used for the preliminary calculation has changed"
      )
    }

    val result = mvc.perform(
      post("/calculation/confirm/$calculationRequestId")
        .accept(APPLICATION_JSON)
        .content(mapper.writeValueAsString(calculationFragments))
        .contentType(APPLICATION_JSON)
    )
      .andExpect(status().isPreconditionFailed)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).contains(
      "The booking data used for the preliminary calculation has changed"
    )
  }

  @Test
  fun `Test GET of calculation results by calculationRequestId`() {
    val calculationRequestId = 9995L
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = calculationRequestId, dates = mapOf(), calculationStatus = PRELIMINARY,
      bookingId = 123L, prisonerId = "ASD"
    )

    whenever(calculationTransactionalService.findCalculationResults(calculationRequestId)).thenReturn(calculatedReleaseDates)

    val result = mvc.perform(get("/calculation/results/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(calculatedReleaseDates))
    verify(calculationTransactionalService, times(1)).findCalculationResults(calculationRequestId)
  }

  @Test
  fun `Test GET of user inputs by calculationRequestId returns 404 if not found`() {
    val calculationRequestId = 9995L

    whenever(calculationTransactionalService.findUserInput(calculationRequestId)).thenReturn(null)

    mvc.perform(get("/calculation/calculation-user-input/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isNotFound)
      .andExpect(content().contentType(APPLICATION_JSON))

    verify(calculationTransactionalService, times(1)).findUserInput(calculationRequestId)
  }
}
