package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import arrow.core.left
import arrow.core.right
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
import org.springframework.http.HttpStatus
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOriginalData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationBreakdownService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationUserQuestionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DetailedCalculationResultsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.LatestCalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RelevantRemandService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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

  @MockBean
  private lateinit var detailedCalculationResultsService: DetailedCalculationResultsService

  @MockBean
  private lateinit var relevantRemandService: RelevantRemandService

  @MockBean
  private lateinit var latestCalculationService: LatestCalculationService

  @MockBean
  private lateinit var calculationBreakdownService: CalculationBreakdownService

  @Autowired
  private lateinit var mvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  private val submitCalculationRequest = SubmitCalculationRequest(
    calculationFragments = CalculationFragments(breakdownHtml = "<p>breakdown</p>"),
    approvedDates = null,
  )

  @BeforeEach
  fun reset() {
    reset(calculationTransactionalService)

    mvc = MockMvcBuilders
      .standaloneSetup(
        CalculationController(
          calculationTransactionalService,
          calculationUserQuestionService,
          relevantRemandService,
          detailedCalculationResultsService,
          latestCalculationService,
          calculationBreakdownService,
        ),
      )
      .setControllerAdvice(ControllerAdvice())
      .build()
  }

  @Test
  fun `Test POST of a PRELIMINARY calculation`() {
    val prisonerId = "A1234AB"
    val bookingId = 9995L

    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L,
      dates = mapOf(),
      calculationStatus = PRELIMINARY,
      bookingId = bookingId,
      prisonerId = prisonerId,
      calculationReference = UUID.randomUUID(),
      calculationReason = CALCULATION_REASON,
      calculationDate = LocalDate.of(2024, 1, 1),
    )

    val calculationRequestModel = CalculationRequestModel(CalculationUserInputs(), -1L, "")

    whenever(calculationTransactionalService.calculate(prisonerId, calculationRequestModel)).thenReturn(
      calculatedReleaseDates,
    )

    val result = mvc.perform(
      post("/calculation/$prisonerId")
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content(mapper.writeValueAsString(calculationRequestModel)),
    )
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, CalculatedReleaseDates::class.java)).isEqualTo(
      calculatedReleaseDates,
    )
    verify(calculationTransactionalService, times(1)).calculate(prisonerId, calculationRequestModel)
  }

  @Test
  fun `Test POST of a PRELIMINARY calculation with user input`() {
    val prisonerId = "A1234AB"
    val bookingId = 9995L
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L,
      dates = mapOf(),
      calculationStatus = PRELIMINARY,
      bookingId = bookingId,
      prisonerId = prisonerId,
      calculationReference = UUID.randomUUID(),
      calculationReason = CALCULATION_REASON,
      calculationDate = LocalDate.of(2024, 1, 1),
    )
    val calculationRequestModel = CalculationRequestModel(CalculationUserInputs(), calculationReasonId = -1L)

    whenever(calculationTransactionalService.calculate(prisonerId, calculationRequestModel)).thenReturn(
      calculatedReleaseDates,
    )

    val result = mvc.perform(
      post("/calculation/$prisonerId")
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content(mapper.writeValueAsString(calculationRequestModel)),
    )
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, CalculatedReleaseDates::class.java)).isEqualTo(
      calculatedReleaseDates,
    )
    verify(calculationTransactionalService, times(1)).calculate(prisonerId, calculationRequestModel)
  }

  @Test
  fun `Test POST to confirm a calculation and that event is published`() {
    val prisonerId = "A1234AB"
    val calculationRequestId = 12345L
    val bookingId = 9995L

    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L,
      dates = mapOf(),
      calculationStatus = PRELIMINARY,
      bookingId = bookingId,
      prisonerId = prisonerId,
      calculationReference = UUID.randomUUID(),
      calculationReason = CALCULATION_REASON,
      calculationDate = LocalDate.of(2024, 1, 1),
    )
    whenever(
      calculationTransactionalService.validateAndConfirmCalculation(
        calculationRequestId,
        submitCalculationRequest,
      ),
    ).thenReturn(calculatedReleaseDates)

    val result = mvc.perform(
      post("/calculation/confirm/$calculationRequestId")
        .accept(APPLICATION_JSON)
        .content(mapper.writeValueAsString(submitCalculationRequest))
        .contentType(APPLICATION_JSON),
    )
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, CalculatedReleaseDates::class.java)).isEqualTo(
      calculatedReleaseDates,
    )
    verify(calculationTransactionalService, times(1)).validateAndConfirmCalculation(
      calculationRequestId,
      submitCalculationRequest,
    )
  }

  @Test
  fun `Test POST to confirm a calculation when the data has changed since the PRELIM calc - results in exception`() {
    val calculationRequestId = 12345L

    whenever(
      calculationTransactionalService.validateAndConfirmCalculation(
        calculationRequestId,
        submitCalculationRequest,
      ),
    ).then {
      throw PreconditionFailedException(
        "The booking data used for the preliminary calculation has changed",
      )
    }

    val result = mvc.perform(
      post("/calculation/confirm/$calculationRequestId")
        .accept(APPLICATION_JSON)
        .content(mapper.writeValueAsString(submitCalculationRequest))
        .contentType(APPLICATION_JSON),
    )
      .andExpect(status().isPreconditionFailed)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).contains(
      "The booking data used for the preliminary calculation has changed",
    )
  }

  @Test
  fun `Test GET of calculation results by calculationRequestId`() {
    val calculationRequestId = 9995L
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = calculationRequestId,
      dates = mapOf(),
      calculationStatus = PRELIMINARY,
      bookingId = 123L,
      prisonerId = "ASD",
      calculationReference = UUID.randomUUID(),
      calculationReason = CALCULATION_REASON,
      calculationDate = LocalDate.of(2024, 1, 1),
    )

    whenever(calculationTransactionalService.findCalculationResults(calculationRequestId)).thenReturn(
      calculatedReleaseDates,
    )

    val result = mvc.perform(get("/calculation/results/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, CalculatedReleaseDates::class.java)).isEqualTo(
      calculatedReleaseDates,
    )
    verify(calculationTransactionalService, times(1)).findCalculationResults(calculationRequestId)
  }

  @Test
  fun `Test GET of calculation results with approved dates`() {
    val calculationRequestId = 9995L
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = calculationRequestId,
      dates = mapOf(),
      calculationStatus = PRELIMINARY,
      bookingId = 123L,
      prisonerId = "ASD",
      approvedDates = mapOf(ReleaseDateType.APD to LocalDate.of(2020, 3, 3)),
      calculationReference = UUID.randomUUID(),
      calculationReason = CALCULATION_REASON,
      calculationDate = LocalDate.of(2024, 1, 1),
    )
    whenever(calculationTransactionalService.findCalculationResults(calculationRequestId)).thenReturn(
      calculatedReleaseDates,
    )

    val result = mvc.perform(get("/calculation/results/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, CalculatedReleaseDates::class.java)).isEqualTo(
      calculatedReleaseDates,
    )
    verify(calculationTransactionalService, times(1)).findCalculationResults(calculationRequestId)
  }

  @Test
  fun `Test GET of calculation breakdown by calculationRequestId`() {
    val calculationRequestId = 9995L
    val breakdown = CalculationBreakdown(listOf(), null)
    whenever(calculationBreakdownService.getBreakdownUnsafely(calculationRequestId)).thenReturn(breakdown)

    val result = mvc.perform(get("/calculation/breakdown/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(result.response.contentAsString).isEqualTo(mapper.writeValueAsString(breakdown))
  }

  @Test
  fun `Test GET of calculation detailed results`() {
    val calculationRequestId = 9995L
    val calculatedReleaseDates = DetailedCalculationResults(
      CalculationContext(calculationRequestId, 1, "A", CONFIRMED, UUID.randomUUID(), null, null, null, CalculationType.CALCULATED),
      dates = mapOf(),
      null,
      CalculationOriginalData(
        null,
        null,
      ),
      null,
    )
    whenever(detailedCalculationResultsService.findDetailedCalculationResults(calculationRequestId)).thenReturn(
      calculatedReleaseDates,
    )

    val result = mvc.perform(get("/calculation/detailed-results/$calculationRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, DetailedCalculationResults::class.java)).isEqualTo(
      calculatedReleaseDates,
    )
    verify(detailedCalculationResultsService, times(1)).findDetailedCalculationResults(calculationRequestId)
  }

  @Test
  fun `Test GET of latest calculation successfully`() {
    val prisonerId = "ABC123"
    val expected = LatestCalculation(
      prisonerId,
      LocalDateTime.now(),
      "HMP Belfast",
      "Other",
      CalculationSource.CRDS,
      mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.description, LocalDate.of(2024, 1, 1), emptyList())),
    )

    whenever(latestCalculationService.latestCalculationForPrisoner(prisonerId)).thenReturn(expected.right())

    val result = mvc.perform(get("/calculation/$prisonerId/latest").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, LatestCalculation::class.java))
      .isEqualTo(expected)
  }

  @Test
  fun `Test GET of latest calculation when there is a problem getting the latest calc`() {
    val prisonerId = "ABC123"
    val errorMessage = "There isn't one"

    whenever(latestCalculationService.latestCalculationForPrisoner(prisonerId)).thenReturn(errorMessage.left())

    val result = mvc.perform(get("/calculation/$prisonerId/latest").accept(APPLICATION_JSON))
      .andExpect(status().isNotFound)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, ErrorResponse::class.java))
      .isEqualTo(
        ErrorResponse(
          HttpStatus.NOT_FOUND,
          null,
          errorMessage,
          errorMessage,
        ),
      )
  }

  private val CALCULATION_REASON = CalculationReason(-1, false, false, "Reason", false, null, null, null)
}
