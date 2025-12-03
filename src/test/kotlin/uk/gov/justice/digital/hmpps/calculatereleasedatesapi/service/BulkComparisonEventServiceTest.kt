package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.eligibility.ErsedEligibilityService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.eligibility.ErsedEligibilityService.ErsedEligibility
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class BulkComparisonEventServiceTest {
  private val prisonService: PrisonService = mock()
  private val calculationSourceDataService: CalculationSourceDataService = mock()
  private val calculationReasonRepository: CalculationReasonRepository = mock()
  private val calculationTransactionalService: CalculationTransactionalService = mock()
  private val comparisonRepository: ComparisonRepository = mock()
  private val comparisonPersonRepository: ComparisonPersonRepository = mock()
  private val objectMapper: ObjectMapper = TestUtil.objectMapper()
  private val ersedEligibilityService: ErsedEligibilityService = mock()
  private val bookingService: BookingService = mock()
  private val validationService: ValidationService = mock()

  private val service = BulkComparisonEventHandlerService(
    prisonService,
    calculationSourceDataService,
    calculationReasonRepository,
    calculationTransactionalService,
    comparisonRepository,
    comparisonPersonRepository,
    objectMapper,
    ersedEligibilityService,
    validationService,
    bookingService,

  )
  private val bookingId = 999L
  private val body = BulkComparisonMessageBody(
    comparisonId = 1L,
    personId = "A1234BC",
    username = "FOO",
    establishment = null,
  )
  private val comparison = Comparison(
    id = 1,
    comparisonReference = UUID.randomUUID(),
    comparisonShortReference = "ABCD1234",
    criteria = objectMapper.createObjectNode(),
    prison = null,
    comparisonType = ComparisonType.MANUAL,
    calculatedAt = LocalDateTime.now(),
    calculatedByUsername = "FOO",
    comparisonStatus = ComparisonStatus.PROCESSING,
  )
  private val calculationReason = CalculationReason(
    id = 1L,
    isActive = false,
    isOther = false,
    displayName = "Reason",
    isBulk = true,
    nomisReason = null,
    nomisComment = null,
    displayRank = null,
    useForApprovedDates = false,
  )
  private val blankSentenceCalcDates = SentenceCalcDates(
    sentenceExpiryCalculatedDate = null,
    sentenceExpiryOverrideDate = null,
    automaticReleaseDate = null,
    automaticReleaseOverrideDate = null,
    conditionalReleaseDate = null,
    conditionalReleaseOverrideDate = null,
    nonParoleDate = null,
    nonParoleOverrideDate = null,
    postRecallReleaseDate = null,
    postRecallReleaseOverrideDate = null,
    licenceExpiryCalculatedDate = null,
    licenceExpiryOverrideDate = null,
    homeDetentionCurfewEligibilityCalculatedDate = null,
    homeDetentionCurfewEligibilityOverrideDate = null,
    paroleEligibilityCalculatedDate = null,
    paroleEligibilityOverrideDate = null,
    homeDetentionCurfewActualDate = null,
    actualParoleDate = null,
    releaseOnTemporaryLicenceDate = null,
    earlyRemovalSchemeEligibilityDate = null,
    tariffEarlyRemovalSchemeEligibilityDate = null,
    tariffDate = null,
    etdCalculatedDate = null,
    etdOverrideDate = null,
    mtdCalculatedDate = null,
    mtdOverrideDate = null,
    ltdCalculatedDate = null,
    ltdOverrideDate = null,
    topupSupervisionExpiryCalculatedDate = null,
    topupSupervisionExpiryOverrideDate = null,
    dtoPostRecallReleaseDate = null,
    dtoPostRecallReleaseDateOverride = null,
    effectiveSentenceEndDate = null,
  )

  @Test
  fun `should not calculate ersed if there wasn't one before`() {
    val prisonerDetails = PrisonerDetails(
      bookingId = bookingId,
      offenderNo = body.personId,
      dateOfBirth = LocalDate.of(1982, 6, 15),
      firstName = "Zimmy",
      lastName = "Cnys",
      sentenceDetail = blankSentenceCalcDates.copy(earlyRemovalSchemeEligibilityDate = null),
    )
    whenever(comparisonRepository.findById(1L)).thenReturn(Optional.of(comparison))
    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(calculationReason))
    whenever(comparisonPersonRepository.findByComparisonIdAndPerson(body.comparisonId, body.personId)).thenReturn(emptyList())
    whenever(prisonService.getOffenderDetail(body.personId)).thenReturn(prisonerDetails)
    whenever(calculationSourceDataService.getCalculationSourceData(same(prisonerDetails), any(), any())).thenReturn(
      CalculationSourceData(emptyList(), prisonerDetails, AdjustmentsSourceData(adjustmentsApiData = emptyList()), emptyList(), null),
    )
    whenever(validationService.validate(any(), any(), any()))
      .thenReturn(emptyList())
    whenever(bookingService.getBooking(any())).thenReturn(mock())
    whenever(calculationTransactionalService.calculate(any(), any(), any(), any(), any(), isNull(), isNull(), any(), isNull(), any()))
      .thenReturn(CalculatedReleaseDates(emptyMap(), 1L, 1L, "ASD", CalculationStatus.BULK, calculationReference = UUID.randomUUID(), calculationReason = null, calculationDate = LocalDate.now(), calculationOutput = null))

    service.handleBulkComparisonMessage(InternalMessage(body))

    val calculationInputsCaptor = argumentCaptor<CalculationUserInputs>()
    verify(validationService).validate(any(), calculationInputsCaptor.capture(), any())
    assertThat(calculationInputsCaptor.firstValue.calculateErsed).isFalse()
    verify(ersedEligibilityService, never()).sentenceIsEligible(any())
  }

  @Test
  fun `should not calculate ersed if there was one before but they are not eligible`() {
    val prisonerDetails = PrisonerDetails(
      bookingId = bookingId,
      offenderNo = body.personId,
      dateOfBirth = LocalDate.of(1982, 6, 15),
      firstName = "Zimmy",
      lastName = "Cnys",
      sentenceDetail = blankSentenceCalcDates.copy(earlyRemovalSchemeEligibilityDate = LocalDate.of(2025, 1, 1)),
    )
    whenever(comparisonRepository.findById(1L)).thenReturn(Optional.of(comparison))
    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(calculationReason))
    whenever(comparisonPersonRepository.findByComparisonIdAndPerson(body.comparisonId, body.personId)).thenReturn(emptyList())
    whenever(prisonService.getOffenderDetail(body.personId)).thenReturn(prisonerDetails)
    whenever(calculationSourceDataService.getCalculationSourceData(same(prisonerDetails), any(), any())).thenReturn(
      CalculationSourceData(emptyList(), prisonerDetails, AdjustmentsSourceData(adjustmentsApiData = emptyList()), emptyList(), null),
    )
    whenever(validationService.validate(any(), any(), any()))
      .thenReturn(emptyList())
    whenever(bookingService.getBooking(any())).thenReturn(mock())
    whenever(calculationTransactionalService.calculate(any(), any(), any(), any(), any(), isNull(), isNull(), any(), isNull(), any()))
      .thenReturn(CalculatedReleaseDates(emptyMap(), 1L, 1L, "ASD", CalculationStatus.BULK, calculationReference = UUID.randomUUID(), calculationReason = null, calculationDate = LocalDate.now(), calculationOutput = null))
    whenever(ersedEligibilityService.sentenceIsEligible(bookingId)).thenReturn(ErsedEligibility(false, "foo"))

    service.handleBulkComparisonMessage(InternalMessage(body))

    val calculationInputsCaptor = argumentCaptor<CalculationUserInputs>()
    verify(validationService).validate(any(), calculationInputsCaptor.capture(), any())
    assertThat(calculationInputsCaptor.firstValue.calculateErsed).isFalse()
    verify(ersedEligibilityService).sentenceIsEligible(bookingId)
  }

  @Test
  fun `should calculate ersed if there was one before and they are eligible`() {
    val prisonerDetails = PrisonerDetails(
      bookingId = bookingId,
      offenderNo = body.personId,
      dateOfBirth = LocalDate.of(1982, 6, 15),
      firstName = "Zimmy",
      lastName = "Cnys",
      sentenceDetail = blankSentenceCalcDates.copy(earlyRemovalSchemeEligibilityDate = LocalDate.of(2025, 1, 1)),
    )
    whenever(comparisonRepository.findById(1L)).thenReturn(Optional.of(comparison))
    whenever(calculationReasonRepository.findTopByIsBulkTrue()).thenReturn(Optional.of(calculationReason))
    whenever(comparisonPersonRepository.findByComparisonIdAndPerson(body.comparisonId, body.personId)).thenReturn(emptyList())
    whenever(prisonService.getOffenderDetail(body.personId)).thenReturn(prisonerDetails)
    whenever(calculationSourceDataService.getCalculationSourceData(same(prisonerDetails), any(), any())).thenReturn(
      CalculationSourceData(emptyList(), prisonerDetails, AdjustmentsSourceData(adjustmentsApiData = emptyList()), emptyList(), null),
    )
    whenever(validationService.validate(any(), any(), any()))
      .thenReturn(emptyList())
    whenever(bookingService.getBooking(any())).thenReturn(mock())
    whenever(calculationTransactionalService.calculate(any(), any(), any(), any(), any(), isNull(), isNull(), any(), isNull(), any()))
      .thenReturn(CalculatedReleaseDates(emptyMap(), 1L, 1L, "ASD", CalculationStatus.BULK, calculationReference = UUID.randomUUID(), calculationReason = null, calculationDate = LocalDate.now(), calculationOutput = null))
    whenever(ersedEligibilityService.sentenceIsEligible(bookingId)).thenReturn(ErsedEligibility(true))

    service.handleBulkComparisonMessage(InternalMessage(body))

    val calculationInputsCaptor = argumentCaptor<CalculationUserInputs>()
    verify(validationService).validate(any(), calculationInputsCaptor.capture(), any())
    assertThat(calculationInputsCaptor.firstValue.calculateErsed).isTrue()
    verify(ersedEligibilityService).sentenceIsEligible(bookingId)
  }
}
