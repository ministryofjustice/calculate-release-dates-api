package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesInputResponse.Companion.available
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesInputResponse.Companion.unavailable
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesUnavailableReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationReasonDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
import java.time.LocalDate
import java.util.*

class ApprovedDatesServiceTest {

  private val calculationRequestRepository: CalculationRequestRepository = mock()
  private val calculationSourceDataService: CalculationSourceDataService = mock()
  private val bookingService: BookingService = mock()
  private val validationService: ValidationService = mock()
  private val calculationReasonRepository: CalculationReasonRepository = mock()
  private val calculationTransactionalService: CalculationTransactionalService = mock()
  private val objectMapper: ObjectMapper = TestUtil.objectMapper()

  private val service = ApprovedDatesService(
    calculationRequestRepository,
    calculationSourceDataService,
    bookingService,
    validationService,
    calculationReasonRepository,
    calculationTransactionalService,
    objectMapper,
  )

  @BeforeEach
  fun setUp() {
    whenever(calculationReasonRepository.getByUseForApprovedDatesIsTrue()).thenReturn(APPROVED_DATES_CALC_REASON)
  }

  @Test
  fun `inputs should return unavailable if there is no previous calc`() {
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.empty())

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(unavailable(ApprovedDatesUnavailableReason.NO_PREVIOUS_CALCULATION))
  }

  @ParameterizedTest
  @CsvSource(value = ["MANUAL_DETERMINATE", "MANUAL_INDETERMINATE"])
  fun `inputs should return unavailable if the previous confirmed calc was a manual calc`(calculationType: CalculationType) {
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(
      Optional.of(
        CalculationRequest(
          calculationType = calculationType,
          calculationStatus = "CONFIRMED",
        ),
      ),
    )

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(unavailable(ApprovedDatesUnavailableReason.PREVIOUS_CALCULATION_MANUAL))
  }

  @Test
  fun `inputs should return unavailable if the previous confirmed calc was a genuine override`() {
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(
      Optional.of(
        CalculationRequest(
          calculationType = CalculationType.GENUINE_OVERRIDE,
          calculationStatus = "CONFIRMED",
        ),
      ),
    )

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(unavailable(ApprovedDatesUnavailableReason.PREVIOUS_CALCULATION_GENUINE_OVERRIDE))
  }

  @Test
  fun `inputs should return unavailable if calculation source data has changed since the previous calculation`() {
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(MINIMAL_CALC_REQUEST))
    whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any())).thenReturn(MINIMAL_SOURCE_DATA)
    whenever(bookingService.getBooking(any())).thenReturn(MINIMAL_BOOKING.copy(Offender(reference = PRISONER_ID, dateOfBirth = LocalDate.of(2010, 12, 28))))

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(unavailable(ApprovedDatesUnavailableReason.INPUTS_CHANGED_SINCE_LAST_CALCULATION))
  }

  @Test
  fun `inputs should return unavailable if there are validation messages`() {
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(MINIMAL_CALC_REQUEST))
    whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any())).thenReturn(MINIMAL_SOURCE_DATA)
    whenever(bookingService.getBooking(any())).thenReturn(MINIMAL_BOOKING)
    whenever(validationService.validate(any(), any(), any())).thenReturn(listOf(ValidationMessage(ValidationCode.OFFENCE_MISSING_DATE, listOf("1", "2"))))

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(unavailable(ApprovedDatesUnavailableReason.VALIDATION_FAILED))
  }

  @Test
  fun `inputs should return unavailable if the calculation fails`() {
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(MINIMAL_CALC_REQUEST))
    whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any())).thenReturn(MINIMAL_SOURCE_DATA)
    whenever(bookingService.getBooking(any())).thenReturn(MINIMAL_BOOKING)
    whenever(validationService.validate(any(), any(), any())).thenReturn(emptyList())
    whenever(calculationTransactionalService.calculate(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("Bang!"))

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(unavailable(ApprovedDatesUnavailableReason.CALCULATION_FAILED))
  }

  @Test
  fun `inputs should return unavailable if the calculation produces different dates to the previous calculation`() {
    val latestCalcRequest = MINIMAL_CALC_REQUEST.copy(
      calculationOutcomes = listOf(
        CalculationOutcome(id = 1, calculationRequestId = 1, calculationDateType = ReleaseDateType.SED.name, outcomeDate = LocalDate.of(2030, 12, 12)),
        CalculationOutcome(id = 2, calculationRequestId = 1, calculationDateType = ReleaseDateType.LED.name, outcomeDate = LocalDate.of(2030, 12, 12)),
        CalculationOutcome(id = 3, calculationRequestId = 1, calculationDateType = ReleaseDateType.CRD.name, outcomeDate = LocalDate.of(2027, 6, 6)),
      ),
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(latestCalcRequest))
    whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any())).thenReturn(MINIMAL_SOURCE_DATA)
    whenever(bookingService.getBooking(any())).thenReturn(MINIMAL_BOOKING)
    whenever(validationService.validate(any(), any(), any())).thenReturn(emptyList())
    whenever(calculationTransactionalService.calculate(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(
        CalculatedReleaseDates(
          calculationRequestId = 9991L,
          dates = mapOf(
            ReleaseDateType.SED to LocalDate.of(2030, 12, 12),
            ReleaseDateType.LED to LocalDate.of(2030, 12, 12),
            ReleaseDateType.CRD to LocalDate.of(2025, 1, 1),
          ),
          calculationStatus = PRELIMINARY,
          bookingId = 1L,
          prisonerId = PRISONER_ID,
          calculationReference = UUID.randomUUID(),
          calculationReason = CalculationReasonDto.from(APPROVED_DATES_CALC_REASON),
          calculationDate = LocalDate.of(2024, 1, 1),
        ),
      )

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(unavailable(ApprovedDatesUnavailableReason.DATES_HAVE_CHANGED))
  }

  @Test
  fun `inputs should return available if the calculation produces the sames dates as the previous calculation (no previously approved dates)`() {
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L,
      dates = mapOf(
        // differing order should be irrelevant
        ReleaseDateType.LED to LocalDate.of(2030, 12, 12),
        ReleaseDateType.CRD to LocalDate.of(2027, 6, 6),
        ReleaseDateType.SED to LocalDate.of(2030, 12, 12),
      ),
      calculationStatus = PRELIMINARY,
      bookingId = 1L,
      prisonerId = PRISONER_ID,
      calculationReference = UUID.randomUUID(),
      calculationReason = CalculationReasonDto.from(APPROVED_DATES_CALC_REASON),
      calculationDate = LocalDate.of(2024, 1, 1),
    )
    val latestCalcRequest = MINIMAL_CALC_REQUEST.copy(
      calculationOutcomes = listOf(
        CalculationOutcome(id = 1, calculationRequestId = 1, calculationDateType = ReleaseDateType.SED.name, outcomeDate = LocalDate.of(2030, 12, 12)),
        CalculationOutcome(id = 2, calculationRequestId = 1, calculationDateType = ReleaseDateType.LED.name, outcomeDate = LocalDate.of(2030, 12, 12)),
        CalculationOutcome(id = 3, calculationRequestId = 1, calculationDateType = ReleaseDateType.CRD.name, outcomeDate = LocalDate.of(2027, 6, 6)),
      ),
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(latestCalcRequest))
    whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any())).thenReturn(MINIMAL_SOURCE_DATA)
    whenever(bookingService.getBooking(any())).thenReturn(MINIMAL_BOOKING)
    whenever(validationService.validate(any(), any(), any())).thenReturn(emptyList())
    whenever(calculationTransactionalService.calculate(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(calculatedReleaseDates)
    whenever(calculationRequestRepository.findLatestCalculationWithApprovedDates(PRISONER_ID)).thenReturn(null)

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(available(calculatedReleaseDates))
  }

  @Test
  fun `available inputs should return the previously entered dates if the hash is the same`() {
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L,
      dates = mapOf(
        ReleaseDateType.LED to LocalDate.of(2030, 12, 12),
      ),
      calculationStatus = PRELIMINARY,
      bookingId = 1L,
      prisonerId = PRISONER_ID,
      calculationReference = UUID.randomUUID(),
      calculationReason = CalculationReasonDto.from(APPROVED_DATES_CALC_REASON),
      calculationDate = LocalDate.of(2024, 1, 1),
    )
    val latestCalcRequest = MINIMAL_CALC_REQUEST.copy(
      calculationOutcomes = listOf(
        CalculationOutcome(id = 2, calculationRequestId = 1, calculationDateType = ReleaseDateType.LED.name, outcomeDate = LocalDate.of(2030, 12, 12)),
      ),
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(latestCalcRequest))
    whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any())).thenReturn(MINIMAL_SOURCE_DATA)
    whenever(bookingService.getBooking(any())).thenReturn(MINIMAL_BOOKING)
    whenever(validationService.validate(any(), any(), any())).thenReturn(emptyList())
    whenever(calculationTransactionalService.calculate(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(calculatedReleaseDates)
    val previousWithApprovedDates = latestCalcRequest.copy(
      id = 3,
      approvedDatesSubmissions = listOf(
        ApprovedDatesSubmission(
          calculationRequest = latestCalcRequest,
          approvedDates = listOf(
            ApprovedDates(
              calculationDateType = "APD",
              outcomeDate = LocalDate.of(2000, 1, 2),
            ),
          ),
          prisonerId = PRISONER_ID,
          bookingId = 1L,
          submittedByUsername = "user1",
        ),
      ),
    )
    whenever(calculationRequestRepository.findLatestCalculationWithApprovedDates(PRISONER_ID)).thenReturn(previousWithApprovedDates)

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(available(calculatedReleaseDates).copy(previousApprovedDates = listOf(ApprovedDate(ReleaseDateType.APD, LocalDate.of(2000, 1, 2)))))
  }

  @Test
  fun `available inputs should not return the previously entered dates if the hash is has changed`() {
    val calculatedReleaseDates = CalculatedReleaseDates(
      calculationRequestId = 9991L,
      dates = mapOf(
        ReleaseDateType.LED to LocalDate.of(2030, 12, 12),
      ),
      calculationStatus = PRELIMINARY,
      bookingId = 1L,
      prisonerId = PRISONER_ID,
      calculationReference = UUID.randomUUID(),
      calculationReason = CalculationReasonDto.from(APPROVED_DATES_CALC_REASON),
      calculationDate = LocalDate.of(2024, 1, 1),
    )
    val latestCalcRequest = MINIMAL_CALC_REQUEST.copy(
      calculationOutcomes = listOf(
        CalculationOutcome(id = 2, calculationRequestId = 1, calculationDateType = ReleaseDateType.LED.name, outcomeDate = LocalDate.of(2030, 12, 12)),
      ),
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(latestCalcRequest))
    whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any())).thenReturn(MINIMAL_SOURCE_DATA)
    whenever(bookingService.getBooking(any())).thenReturn(MINIMAL_BOOKING)
    whenever(validationService.validate(any(), any(), any())).thenReturn(emptyList())
    whenever(calculationTransactionalService.calculate(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(calculatedReleaseDates)
    val previousWithApprovedDates = latestCalcRequest.copy(
      id = 3,
      inputData = objectToJson(MINIMAL_BOOKING.copy(returnToCustodyDate = LocalDate.now()), TestUtil.objectMapper()),
      approvedDatesSubmissions = listOf(
        ApprovedDatesSubmission(
          calculationRequest = latestCalcRequest,
          approvedDates = listOf(
            ApprovedDates(
              calculationDateType = "APD",
              outcomeDate = LocalDate.of(2000, 1, 2),
            ),
          ),
          prisonerId = PRISONER_ID,
          bookingId = 1L,
          submittedByUsername = "user1",
        ),
      ),
    )
    whenever(calculationRequestRepository.findLatestCalculationWithApprovedDates(PRISONER_ID)).thenReturn(previousWithApprovedDates)

    val response = service.inputsForPrisoner(PRISONER_ID)

    assertThat(response).isEqualTo(available(calculatedReleaseDates).copy(previousApprovedDates = emptyList()))
  }

  companion object {
    private const val PRISONER_ID = "A1234BC"
    private val MINIMAL_SOURCE_DATA = CalculationSourceData(
      sentenceAndOffences = emptyList(),
      prisonerDetails = PrisonerDetails(
        bookingId = 1,
        offenderNo = PRISONER_ID,
        dateOfBirth = LocalDate.of(1982, 6, 15),
      ),
      bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()),
      returnToCustodyDate = null,
    )
    private val MINIMAL_BOOKING = Booking(
      offender = Offender(
        reference = PRISONER_ID,
        dateOfBirth = LocalDate.of(1982, 6, 15),
      ),
      sentences = emptyList(),
    )
    private val MINIMAL_CALC_REQUEST = CalculationRequest(
      prisonerId = PRISONER_ID,
      calculationType = CalculationType.CALCULATED,
      calculationStatus = "CONFIRMED",
      inputData = objectToJson(MINIMAL_BOOKING, TestUtil.objectMapper()),
    )
    private val APPROVED_DATES_CALC_REASON = CalculationReason(
      id = 6L,
      isActive = true,
      isOther = false,
      displayName = "Recording a non-calculated date (including HDCAD, APD or ROTL)",
      isBulk = false,
      nomisReason = "UPDATE",
      nomisComment = "Recording a non-calculated date",
      displayRank = 99,
      useForApprovedDates = true,
      eligibleForPreviouslyRecordedSled = false,
      requiresFurtherDetail = false,
    )
  }
}
