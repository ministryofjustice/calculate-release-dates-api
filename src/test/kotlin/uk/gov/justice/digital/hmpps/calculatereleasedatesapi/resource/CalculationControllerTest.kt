package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import arrow.core.left
import arrow.core.right
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoActiveBookingException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOriginalData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationReasonDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDatesAndCalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationBreakdownService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DetailedCalculationResultsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.LatestCalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.OffenderKeyDatesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RelevantRemandService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@WebMvcTest(controllers = [CalculationController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = [CalculationController::class])
@WebAppConfiguration
class CalculationControllerTest {
  @MockitoBean
  private lateinit var calculationTransactionalService: CalculationTransactionalService

  @MockitoBean
  private lateinit var detailedCalculationResultsService: DetailedCalculationResultsService

  @MockitoBean
  private lateinit var relevantRemandService: RelevantRemandService

  @MockitoBean
  private lateinit var latestCalculationService: LatestCalculationService

  @MockitoBean
  private lateinit var calculationBreakdownService: CalculationBreakdownService

  @MockitoBean
  private lateinit var offenderKeyDatesService: OffenderKeyDatesService

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
          relevantRemandService,
          detailedCalculationResultsService,
          latestCalculationService,
          calculationBreakdownService,
          offenderKeyDatesService,
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
      calculationReason = CalculationReasonDto.from(calculationReason),
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
      calculationReason = CalculationReasonDto.from(calculationReason),
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
      calculationReason = CalculationReasonDto.from(calculationReason),
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
      calculationReason = CalculationReasonDto.from(calculationReason),
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
      calculationReason = CalculationReasonDto.from(calculationReason),
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
    val breakdown = CalculationBreakdown(listOf(), null, ersedNotApplicableDueToDtoLaterThanCrd = false)
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
      CalculationContext(calculationRequestId, 1, "A", CONFIRMED, UUID.randomUUID(), null, null, null, CalculationType.CALCULATED, null, null, false, "username", "User Name", "BXI", "Brixton (HMP)"),
      dates = mapOf(),
      null,
      CalculationOriginalData(
        null,
        null,
      ),
      CalculationBreakdown(listOf(), null, ersedNotApplicableDueToDtoLaterThanCrd = true),
      null,
      SDSEarlyReleaseTranche.TRANCHE_1,
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
      123456,
      LocalDateTime.now(),
      654321,
      "HMP Belfast",
      "Other",
      CalculationSource.CRDS,
      listOf(DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.description, LocalDate.of(2024, 1, 1), emptyList())),
      "username",
      "User Name",
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
          "No active booking available: $errorMessage",
          errorMessage,
        ),
      )
  }

  @Test
  fun `Test GET of offender Key Dates when there is a problem getting the dates from Prison Service - NOMIS`() {
    val offenderSentCalcId = 5636121L
    val errorMessage = "No active booking"

    whenever(
      offenderKeyDatesService.getNomisCalculationSummary(any()),
    ).then {
      throw NoActiveBookingException(errorMessage)
    }

    val result = mvc.perform(get("/calculation/nomis-calculation-summary/$offenderSentCalcId").accept(APPLICATION_JSON))
      .andExpect(status().isNotFound)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, ErrorResponse::class.java))
      .isEqualTo(
        ErrorResponse(
          HttpStatus.NOT_FOUND,
          null,
          "No active booking available: $errorMessage",
          errorMessage,
        ),
      )
  }

  @Test
  fun `Test GET of offender key dates for offenderSentCalcId successfully`() {
    val offenderSentCalcId = 5636121L
    val expected = NomisCalculationSummary(
      "Further Sentence",
      LocalDateTime.of(2024, 2, 29, 10, 30),
      null,
      listOf(
        DetailedDate(
          ReleaseDateType.HDCED,
          ReleaseDateType.HDCED.description,
          LocalDate.of(2024, 1, 1),
          emptyList(),
        ),
      ),
      "foo",
      "bar",
    )

    whenever(offenderKeyDatesService.getNomisCalculationSummary(any())).thenReturn(expected)

    val result = mvc.perform(get("/calculation/nomis-calculation-summary/$offenderSentCalcId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, NomisCalculationSummary::class.java))
      .isEqualTo(expected)
  }

  @Test
  fun `Test GET of offender key dates for a booking id successfully`() {
    val calcRequestId = 5636121L
    val bookingId = 123456L
    val expected = ReleaseDatesAndCalculationContext(
      CalculationContext(
        calcRequestId,
        bookingId,
        "A1234AB",
        CONFIRMED,
        UUID.randomUUID(),
        CalculationReasonDto(-1, isOther = false, displayName = "14 day check", useForApprovedDates = false),
        null,
        LocalDate.of(2024, 1, 1),
        CalculationType.CALCULATED,
        null,
        null,
        false,
        "username",
        "User Name",
        "BXI",
        "Brixton (HMP)",
      ),
      listOf(
        DetailedDate(
          ReleaseDateType.HDCED,
          ReleaseDateType.HDCED.description,
          LocalDate.of(2024, 1, 1),
          emptyList(),
        ),
      ),
    )

    whenever(offenderKeyDatesService.getKeyDatesByCalcId(any())).thenReturn(expected)

    val result = mvc.perform(get("/calculation/release-dates/$calcRequestId").accept(APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn()

    assertThat(mapper.readValue(result.response.contentAsString, ReleaseDatesAndCalculationContext::class.java))
      .isEqualTo(expected)
  }

  @Test
  fun `Test GET of release Dates when there is a problem getting the release dates using CalcReleaseId`() {
    val calcRequestId = 5636121L
    val errorMessage = "There isn't one"

    whenever(
      offenderKeyDatesService.getKeyDatesByCalcId(any()),
    ).then {
      throw CrdWebException(errorMessage, HttpStatus.NOT_FOUND)
    }

    val result = mvc.perform(get("/calculation/release-dates/$calcRequestId").accept(APPLICATION_JSON))
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

  private val calculationReason = CalculationReason(-1, false, false, "Reason", false, null, null, null, false, false)
}
