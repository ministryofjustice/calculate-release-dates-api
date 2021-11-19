package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.reset
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import java.time.LocalDate

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@WebMvcTest(controllers = [CalculationController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = [CalculationController::class])
@WebAppConfiguration
class CalculationControllerTest {

  @MockBean
  private lateinit var bookingService: BookingService

  @MockBean
  private lateinit var calculationService: CalculationService

  @Autowired
  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  @BeforeEach
  fun reset() {
    reset(bookingService)
    reset(calculationService)

    mvc = MockMvcBuilders
      .standaloneSetup(CalculationController(bookingService, calculationService))
      .setControllerAdvice(ControllerAdvice())
      .build()
  }

  @Test
  fun `Test POST of a PRELIMINARY calculation`() {
    val prisonerId = "A1234AB"
    val bookingId = 9995L
    val offender = Offender(prisonerId, "John Doe", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(), mutableMapOf(), bookingId)

    val bookingCalculation = BookingCalculation(calculationRequestId = 9991L)
    whenever(bookingService.getBooking(prisonerId)).thenReturn(booking)
    whenever(calculationService.calculate(booking, PRELIMINARY)).thenReturn(bookingCalculation)

    val result = mvc.perform(post("/calculation/$prisonerId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(bookingCalculation))
    verify(calculationService, times(1)).calculate(booking, PRELIMINARY)
  }

  @Test
  fun `Test POST to confirm a calculation and that event is published`() {
    val prisonerId = "A1234AB"
    val calculationRequestId = 12345
    val bookingId = 9995L
    val offender = Offender(prisonerId, "John Doe", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(), mutableMapOf(), bookingId)

    val bookingCalculation = BookingCalculation(calculationRequestId = 9991L)
    whenever(bookingService.getBooking(prisonerId)).thenReturn(booking)
    whenever(calculationService.calculate(booking, CONFIRMED)).thenReturn(bookingCalculation)

    val result = mvc.perform(post("/calculation/$prisonerId/confirm/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(bookingCalculation))
    verify(calculationService, times(1)).calculate(booking, CONFIRMED)
    verify(calculationService, times(1)).writeToNomisAndPublishEvent(prisonerId, booking, bookingCalculation)
  }

  @Test
  fun `Test POST to confirm a calculation when the data has changed since the PRELIM calc - results in exception`() {
    val prisonerId = "A1234AB"
    val calculationRequestId = 12345L
    val bookingId = 9995L
    val offender = Offender(prisonerId, "John Doe", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(), mutableMapOf(), bookingId)

    val bookingCalculation = BookingCalculation(calculationRequestId = 9991L)
    whenever(bookingService.getBooking(prisonerId)).thenReturn(booking)
    whenever(calculationService.calculate(booking, CONFIRMED)).thenReturn(bookingCalculation)
    whenever(calculationService.validateConfirmationRequest(any(), any())).then {
      throw PreconditionFailedException(
        "The booking data used for the preliminary calculation has changed"
      )
    }

    val result = mvc.perform(post("/calculation/$prisonerId/confirm/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isPreconditionFailed)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).contains(
      "The booking data used for the preliminary calculation has " +
        "changed"
    )
  }

  @Test
  fun `Test GET of calculation results by calculationRequestId`() {
    val calculationRequestId = 9995L
    val bookingCalculation = BookingCalculation(calculationRequestId = calculationRequestId)

    whenever(calculationService.findCalculationResults(calculationRequestId)).thenReturn(bookingCalculation)

    val result = mvc.perform(get("/calculation/results/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(bookingCalculation))
    verify(calculationService, times(1)).findCalculationResults(calculationRequestId)
  }

  @Test
  fun `Test GET of calculation breakdown by calculationRequestId`() {
    val prisonerId = "A1234AB"
    val calculationRequestId = 9995L
    val bookingId = 9995L
    val offender = Offender(prisonerId, "John Doe", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(), mutableMapOf(), bookingId)
    val breakdown = CalculationBreakdown(listOf(), null)

    whenever(calculationService.getBooking(calculationRequestId)).thenReturn(booking)
    whenever(calculationService.calculateWithBreakdown(booking)).thenReturn(breakdown)

    val result = mvc.perform(get("/calculation/breakdown/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(breakdown))
  }
}
