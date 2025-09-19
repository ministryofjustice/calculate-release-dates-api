package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.TestBuildPropertiesConfiguration.Companion.TEST_BUILD_PROPERTIES
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalServiceTest.Companion.BOOKING
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CalculationTransactionalServiceValidationTest {
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val calculationConfirmationService = mock<CalculationConfirmationService>()
  private val dominantHistoricCalculationOutcomeRepository = mock<CalculationOutcomeHistoricOverrideRepository>()
  private val dominantHistoricDateService = DominantHistoricDateService()
  private val prisonService = mock<PrisonService>()
  private val calculationSourceDataService = mock<CalculationSourceDataService>()
  private val eventService = mock<EventService>()
  private val bookingService = mock<BookingService>()
  private val calculationService = mock<CalculationService>()
  private val validationService = mock<ValidationService>()
  private val serviceUserService = mock<ServiceUserService>()
  private val nomisCommentService = mock<NomisCommentService>()
  private val trancheOutcomeRepository = mock<TrancheOutcomeRepository>()

  @Test
  fun `fullValidation calls functions as expected`() {
    val prisonerId = "A1234BC"
    val calculationUserInputs = CalculationUserInputs()
    val fakeMessages = listOf<ValidationMessage>()
    val calculationOutput = CalculationOutput(listOf(), listOf(), mock<CalculationResult>())

    // Mocking the behaviour of services
    whenever(calculationSourceDataService.getCalculationSourceData(prisonerId, InactiveDataOptions.default())).thenReturn(fakeSourceData)
    whenever(validationService.validateBeforeCalculation(any(), eq(calculationUserInputs), any())).thenReturn(fakeMessages)
    whenever(bookingService.getBooking(any())).thenReturn(BOOKING)
    whenever(calculationService.calculateReleaseDates(any(), eq(calculationUserInputs))).thenReturn(calculationOutput)
    whenever(validationService.validateBookingAfterCalculation(any(), any())).thenReturn(fakeMessages)

    // Call the method under test
    calculationTransactionalService().fullValidation(prisonerId, calculationUserInputs)

    val inOrder = inOrder(validationService)
    inOrder.verify(validationService).validateBeforeCalculation(any(), eq(calculationUserInputs), any())
    inOrder.verify(validationService).validateBeforeCalculation(any())
    inOrder.verify(validationService).validateBookingAfterCalculation(any(), any())

    inOrder.verifyNoMoreInteractions()
  }

  private fun calculationTransactionalService(): CalculationTransactionalService {
    val sourceDataMapper = SourceDataMapper(TestUtil.objectMapper())

    return CalculationTransactionalService(
      calculationRequestRepository,
      calculationOutcomeRepository,
      calculationReasonRepository,
      dominantHistoricCalculationOutcomeRepository,
      TestUtil.objectMapper(),
      calculationSourceDataService,
      sourceDataMapper,
      calculationService,
      bookingService,
      validationService,
      serviceUserService,
      calculationConfirmationService,
      dominantHistoricDateService,
      TEST_BUILD_PROPERTIES,
      trancheOutcomeRepository,
      FeatureToggles(historicSled = true),
    )
  }

  private val fakeSourceData = CalculationSourceData(
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
}
