package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.argThat
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.TestBuildPropertiesConfiguration.Companion.TEST_BUILD_PROPERTIES
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideInputResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideMode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviousGenuineOverride
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.DateValidationService
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.*

class GenuineOverrideServiceTest {
  private val calculationRequestRepository: CalculationRequestRepository = mock<CalculationRequestRepository>()
  private val manualCalculationService: ManualCalculationService = mock<ManualCalculationService>()
  private val serviceUserService: ServiceUserService = mock<ServiceUserService>()
  private val bookingService: BookingService = mock<BookingService>()
  private val calculationSourceDataService: CalculationSourceDataService = mock<CalculationSourceDataService>()
  private val calculationOutcomeRepository: CalculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val dateValidationService: DateValidationService = mock<DateValidationService>()
  private val buildProperties = TEST_BUILD_PROPERTIES
  private val objectMapper = TestUtil.objectMapper()
  private val genuineOverrideService = GenuineOverrideService(
    calculationRequestRepository,
    manualCalculationService,
    serviceUserService,
    bookingService,
    calculationSourceDataService,
    calculationOutcomeRepository,
    buildProperties,
    objectMapper,
    dateValidationService,
  )

  @Test
  fun `should create new request, update the old one and save to NOMIS`() {
    val originalRequest = CalculationRequest(
      id = 123L,
      prisonerId = PRISONER_ID,
      calculationStatus = CalculationStatus.PRELIMINARY.name,
      reasonForCalculation = REASON,
      otherReasonForCalculation = "Other reason",
    )
    val newRequest = CalculationRequest(
      id = 456L,
      prisonerId = PRISONER_ID,
      calculationStatus = CalculationStatus.CONFIRMED.name,
      reasonForCalculation = REASON,
      otherReasonForCalculation = "Other reason",
    )
    val calculationOutcomes = listOf(
      CalculationOutcome(
        id = 1,
        calculationRequestId = newRequest.id,
        outcomeDate = LocalDate.of(2025, 1, 2),
        calculationDateType = "SED",
      ),
      CalculationOutcome(
        id = 2,
        calculationRequestId = newRequest.id,
        outcomeDate = LocalDate.of(2029, 12, 13),
        calculationDateType = "LED",
      ),
      CalculationOutcome(
        id = 3,
        calculationRequestId = newRequest.id,
        outcomeDate = LocalDate.of(2021, 6, 7),
        calculationDateType = "HDCED",
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_ID, InactiveDataOptions.default())).thenReturn(FAKE_SOURCE_DATA)
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA)).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn("USER1")
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(123L, "PRELIMINARY")).thenReturn(Optional.of(originalRequest))
    whenever(calculationRequestRepository.save(argThat { request -> request?.id == -1L })).thenReturn(newRequest)
    whenever(calculationRequestRepository.save(argThat { request -> request?.id == 123L })).thenReturn(originalRequest)
    whenever(manualCalculationService.calculateEffectiveSentenceLength(BOOKING, LocalDate.of(2025, 1, 2))).thenReturn(Period.ZERO)
    whenever(calculationOutcomeRepository.saveAll<CalculationOutcome>(any())).thenReturn(calculationOutcomes)

    val result = genuineOverrideService.overrideDatesForACalculation(
      123L,
      GenuineOverrideRequest(
        dates = listOf(
          GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 1, 2)),
          GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2029, 12, 13)),
          GenuineOverrideDate(ReleaseDateType.HDCED, LocalDate.of(2021, 6, 7)),
        ),
        reason = GenuineOverrideReason.OTHER,
        reasonFurtherDetail = "A test reason",
      ),
    )
    assertThat(result.originalCalculationRequestId).isEqualTo(123L)

    verify(manualCalculationService).writeToNomisAndPublishEvent(
      PRISONER_ID,
      BOOKING,
      newRequest.id,
      calculationOutcomes,
      true,
      Period.ZERO,
    )
  }

  @Test
  fun `should return standard override inputs if previous calc not a genuine override `() {
    val previousRequest = CalculationRequest(
      id = 123L,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING.bookingId,
      calculationStatus = CalculationStatus.CONFIRMED.name,
      calculationType = CalculationType.CALCULATED,
      reasonForCalculation = REASON,
      otherReasonForCalculation = "Other reason",
    )
    val preliminaryRequest = CalculationRequest(
      id = 456L,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING.bookingId,
      calculationStatus = CalculationStatus.CONFIRMED.name,
      reasonForCalculation = REASON,
      otherReasonForCalculation = "Other reason",
      calculationOutcomes = listOf(
        CalculationOutcome(
          id = 1,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2025, 1, 2),
          calculationDateType = "SED",
        ),
        CalculationOutcome(
          id = 2,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2029, 12, 13),
          calculationDateType = "LED",
        ),
        CalculationOutcome(
          id = 3,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2021, 6, 7),
          calculationDateType = "HDCED",
        ),
      ),
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(previousRequest))
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(123L, "PRELIMINARY")).thenReturn(Optional.of(preliminaryRequest))

    val result = genuineOverrideService.inputsForCalculation(
      123L,
    )
    assertThat(result).isEqualTo(
      GenuineOverrideInputResponse(
        mode = GenuineOverrideMode.STANDARD,
        calculatedDates = listOf(
          GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 1, 2)),
          GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2029, 12, 13)),
          GenuineOverrideDate(ReleaseDateType.HDCED, LocalDate.of(2021, 6, 7)),
        ),
        previousOverrideForExpressGenuineOverride = null,
      ),
    )
  }

  @Test
  fun `should return standard override inputs if previous calc was a genuine override but the inputs are different`() {
    val previousRequest = CalculationRequest(
      id = 123L,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING.bookingId,
      calculationStatus = CalculationStatus.CONFIRMED.name,
      calculationType = CalculationType.GENUINE_OVERRIDE,
      reasonForCalculation = REASON,
      otherReasonForCalculation = "Other reason",
      genuineOverrideReason = GenuineOverrideReason.OTHER,
      genuineOverrideReasonFurtherDetail = "Foo",
      overridesCalculationRequestId = 87914565156L,
      inputData = JacksonUtil.toJsonNode("""{"foo": "bar1"}"""),
    )
    val preliminaryRequest = CalculationRequest(
      id = 456L,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING.bookingId,
      calculationStatus = CalculationStatus.CONFIRMED.name,
      reasonForCalculation = REASON,
      otherReasonForCalculation = "Other reason",
      inputData = JacksonUtil.toJsonNode("""{"foo": "bar2"}"""),
      calculationOutcomes = listOf(
        CalculationOutcome(
          id = 1,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2025, 1, 2),
          calculationDateType = "SED",
        ),
        CalculationOutcome(
          id = 2,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2029, 12, 13),
          calculationDateType = "LED",
        ),
        CalculationOutcome(
          id = 3,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2021, 6, 7),
          calculationDateType = "HDCED",
        ),
      ),
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(previousRequest))
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(123L, "PRELIMINARY")).thenReturn(Optional.of(preliminaryRequest))

    val result = genuineOverrideService.inputsForCalculation(
      123L,
    )
    assertThat(result).isEqualTo(
      GenuineOverrideInputResponse(
        mode = GenuineOverrideMode.STANDARD,
        calculatedDates = listOf(
          GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 1, 2)),
          GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2029, 12, 13)),
          GenuineOverrideDate(ReleaseDateType.HDCED, LocalDate.of(2021, 6, 7)),
        ),
        previousOverrideForExpressGenuineOverride = null,
      ),
    )
  }

  @Test
  fun `should return express override inputs if previous calc was a genuine override and the inputs are the same`() {
    val previousRequest = CalculationRequest(
      id = 123L,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING.bookingId,
      calculationStatus = CalculationStatus.CONFIRMED.name,
      calculationType = CalculationType.GENUINE_OVERRIDE,
      reasonForCalculation = REASON,
      otherReasonForCalculation = "Other reason",
      genuineOverrideReason = GenuineOverrideReason.OTHER,
      genuineOverrideReasonFurtherDetail = "Foo",
      overridesCalculationRequestId = 87914565156L,
      inputData = JacksonUtil.toJsonNode("""{"foo": "bar1"}"""),
      calculationOutcomes = listOf(
        CalculationOutcome(
          id = 1,
          calculationRequestId = 123L,
          outcomeDate = LocalDate.of(2035, 1, 2),
          calculationDateType = "SED",
        ),
        CalculationOutcome(
          id = 2,
          calculationRequestId = 123L,
          outcomeDate = LocalDate.of(2039, 12, 13),
          calculationDateType = "LED",
        ),
        CalculationOutcome(
          id = 3,
          calculationRequestId = 123L,
          outcomeDate = LocalDate.of(2031, 6, 7),
          calculationDateType = "HDCED",
        ),
        CalculationOutcome(
          id = 4,
          calculationRequestId = 123L,
          outcomeDate = LocalDate.of(2031, 7, 6),
          calculationDateType = "ERSED",
        ),
      ),

    )
    val preliminaryRequest = CalculationRequest(
      id = 456L,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING.bookingId,
      calculationStatus = CalculationStatus.CONFIRMED.name,
      reasonForCalculation = REASON,
      otherReasonForCalculation = "Other reason",
      inputData = JacksonUtil.toJsonNode("""{"foo": "bar1"}"""),
      calculationOutcomes = listOf(
        CalculationOutcome(
          id = 1,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2025, 1, 2),
          calculationDateType = "SED",
        ),
        CalculationOutcome(
          id = 2,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2029, 12, 13),
          calculationDateType = "LED",
        ),
        CalculationOutcome(
          id = 3,
          calculationRequestId = 456L,
          outcomeDate = LocalDate.of(2021, 6, 7),
          calculationDateType = "HDCED",
        ),
      ),
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(PRISONER_ID, "CONFIRMED")).thenReturn(Optional.of(previousRequest))
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(123L, "PRELIMINARY")).thenReturn(Optional.of(preliminaryRequest))

    val result = genuineOverrideService.inputsForCalculation(
      123L,
    )
    assertThat(result).isEqualTo(
      GenuineOverrideInputResponse(
        mode = GenuineOverrideMode.EXPRESS,
        calculatedDates = listOf(
          GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 1, 2)),
          GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2029, 12, 13)),
          GenuineOverrideDate(ReleaseDateType.HDCED, LocalDate.of(2021, 6, 7)),
        ),
        previousOverrideForExpressGenuineOverride = PreviousGenuineOverride(
          calculationRequestId = 123L,
          dates = listOf(
            GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2035, 1, 2)),
            GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2039, 12, 13)),
            GenuineOverrideDate(ReleaseDateType.HDCED, LocalDate.of(2031, 6, 7)),
            GenuineOverrideDate(ReleaseDateType.ERSED, LocalDate.of(2031, 7, 6)),
          ),
          reason = GenuineOverrideReason.OTHER,
          reasonFurtherDetail = "Foo",
        ),
      ),
    )
  }

  companion object {
    private const val PRISONER_ID = "A1234BC"
    private val OFFENDER = Offender(PRISONER_ID, LocalDate.of(1980, 1, 1))

    private val StandardSENTENCE = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2021, 2, 3),
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 0L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 5L,
        ),
      ),
      offence = Offence(committedAt = LocalDate.of(2021, 2, 3)),
      identifier = UUID.fromString("5ac7a5ae-fa7b-4b57-a44f-8eddde24f5fa"),
      caseSequence = 1,
      lineSequence = 2,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    private val BOOKING = Booking(OFFENDER, listOf(StandardSENTENCE), Adjustments(), null, null, 456789L)
    private val FAKE_SOURCE_DATA = CalculationSourceData(
      emptyList(),
      PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3)),
      AdjustmentsSourceData(
        prisonApiData = BookingAndSentenceAdjustments(
          emptyList(),
          emptyList(),
        ),
      ),
      listOf(),
      null,
    )
    private val REASON = CalculationReason(0, false, false, "Some reason", false, null, null, null)
  }
}
