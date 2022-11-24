package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.reset
import org.mockito.kotlin.any
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationUserQuestionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.math.BigDecimal
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
  private lateinit var calculationTransactionalService: CalculationTransactionalService

  @MockBean
  private lateinit var validationService: ValidationService

  @MockBean
  private lateinit var prisonService: PrisonService

  @MockBean
  private lateinit var calculationService: CalculationService

  @MockBean
  private lateinit var calculationUserQuestionService: CalculationUserQuestionService

  @Autowired
  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  private val sentences: List<SentenceAndOffences> = emptyList()
  private val prisonerDetails = PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3))
  private val adjustments = BookingAndSentenceAdjustments(emptyList(), emptyList())
  private val offenderFineFinePayment = listOf(OffenderFinePayment(bookingId = 1, paymentDate = LocalDate.of(1, 2, 3), paymentAmount = BigDecimal("10000.88")))
  private val sourceData = PrisonApiSourceData(sentences, prisonerDetails, adjustments, offenderFineFinePayment, null)
  private val calculationFragments = CalculationFragments("<p>breakdown</p>")

  @BeforeEach
  fun reset() {
    reset(bookingService)
    reset(calculationTransactionalService)

    mvc = MockMvcBuilders
      .standaloneSetup(CalculationController(bookingService, prisonService, calculationTransactionalService, calculationService, validationService, calculationUserQuestionService))
      .setControllerAdvice(ControllerAdvice())
      .build()
  }

  @Test
  fun `Test POST of a PRELIMINARY calculation`() {
    val prisonerId = "A1234AB"
    val bookingId = 9995L
    val offender = Offender(prisonerId, LocalDate.of(1980, 1, 1))

    val booking = Booking(offender, mutableListOf(), Adjustments(), null, bookingId)

    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L, dates = mapOf(), calculationStatus = PRELIMINARY,
      bookingId = bookingId, prisonerId = prisonerId
    )
    whenever(prisonService.getPrisonApiSourceData(prisonerId)).thenReturn(sourceData)
    whenever(bookingService.getBooking(sourceData, null)).thenReturn(booking)
    whenever(calculationTransactionalService.calculate(booking, PRELIMINARY, sourceData, null)).thenReturn(calculatedReleaseDates)

    val result = mvc.perform(post("/calculation/$prisonerId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(calculatedReleaseDates))
    verify(calculationTransactionalService, times(1)).calculate(booking, PRELIMINARY, sourceData, null)
  }

  @Test
  fun `Test POST of a PRELIMINARY calculation with user input`() {
    val prisonerId = "A1234AB"
    val bookingId = 9995L
    val offender = Offender(prisonerId, LocalDate.of(1980, 1, 1))

    val booking = Booking(offender, mutableListOf(), Adjustments(), null, bookingId)
    val userInput = CalculationUserInputs(listOf(CalculationSentenceUserInput(1, "ABC", UserInputType.ORIGINAL, true)))
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L, dates = mapOf(), calculationStatus = PRELIMINARY,
      bookingId = bookingId, prisonerId = prisonerId
    )
    whenever(prisonService.getPrisonApiSourceData(prisonerId)).thenReturn(sourceData)
    whenever(bookingService.getBooking(sourceData, userInput)).thenReturn(booking)
    whenever(calculationTransactionalService.calculate(booking, PRELIMINARY, sourceData, userInput)).thenReturn(calculatedReleaseDates)

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
    verify(calculationTransactionalService, times(1)).calculate(booking, PRELIMINARY, sourceData, userInput)
  }

  @Test
  fun `Test POST to confirm a calculation and that event is published`() {
    val prisonerId = "A1234AB"
    val calculationRequestId = 12345L
    val bookingId = 9995L
    val offender = Offender(prisonerId, LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(), Adjustments(), null, bookingId,)

    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L, dates = mapOf(), calculationStatus = PRELIMINARY,
      bookingId = bookingId, prisonerId = prisonerId
    )
    whenever(calculationTransactionalService.findUserInput(calculationRequestId)).thenReturn(null)
    whenever(prisonService.getPrisonApiSourceData(prisonerId)).thenReturn(sourceData)
    whenever(bookingService.getBooking(sourceData, null)).thenReturn(booking)
    whenever(calculationTransactionalService.calculate(booking, CONFIRMED, sourceData, null, calculationFragments)).thenReturn(calculatedReleaseDates)

    val result = mvc.perform(
      post("/calculation/$prisonerId/confirm/$calculationRequestId")
        .accept(APPLICATION_JSON)
        .content(mapper.writeValueAsString(calculationFragments))
        .contentType(APPLICATION_JSON)
    )
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(calculatedReleaseDates))
    verify(calculationTransactionalService, times(1)).calculate(booking, CONFIRMED, sourceData, null, calculationFragments)
    verify(calculationTransactionalService, times(1)).writeToNomisAndPublishEvent(prisonerId, booking, calculatedReleaseDates)
  }

  @Test
  fun `Test POST to confirm a calculation when the data has changed since the PRELIM calc - results in exception`() {
    val prisonerId = "A1234AB"
    val calculationRequestId = 12345L
    val bookingId = 9995L
    val offender = Offender(prisonerId, LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(), Adjustments(), null, bookingId)

    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L, dates = mapOf(), calculationStatus = PRELIMINARY,
      bookingId = bookingId, prisonerId = prisonerId
    )
    whenever(calculationTransactionalService.findUserInput(calculationRequestId)).thenReturn(null)
    whenever(prisonService.getPrisonApiSourceData(prisonerId)).thenReturn(sourceData)
    whenever(bookingService.getBooking(sourceData, null)).thenReturn(booking)
    whenever(calculationTransactionalService.calculate(booking, CONFIRMED, sourceData, null)).thenReturn(calculatedReleaseDates)
    whenever(calculationTransactionalService.validateConfirmationRequest(any(), any())).then {
      throw PreconditionFailedException(
        "The booking data used for the preliminary calculation has changed"
      )
    }

    val result = mvc.perform(
      post("/calculation/$prisonerId/confirm/$calculationRequestId")
        .accept(APPLICATION_JSON)
        .content(mapper.writeValueAsString(calculationFragments))
        .contentType(APPLICATION_JSON)
    )
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
  fun `Test GET of calculation breakdown by calculationRequestId`() {
    val calculationRequestId = 9995L
    val breakdown = CalculationBreakdown(listOf(), null)
    whenever(bookingService.getCalculationBreakdown(calculationRequestId)).thenReturn(breakdown)

    val result = mvc.perform(get("/calculation/breakdown/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(breakdown))
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
