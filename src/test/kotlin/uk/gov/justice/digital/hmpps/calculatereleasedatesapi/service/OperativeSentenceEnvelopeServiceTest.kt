package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.left
import arrow.core.right
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.OperativeSentenceEnvelopeEntity
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelopeSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.OperativeSentenceEnvelopeRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class OperativeSentenceEnvelopeServiceTest {

  private val prisonService = mock<PrisonService>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val operativeSentenceEnvelopeRepository = mock<OperativeSentenceEnvelopeRepository>()

  private val service = OperativeSentenceEnvelopeService(
    prisonService = prisonService,
    calculationRequestRepository = calculationRequestRepository,
    operativeSentenceEnvelopeRepository = operativeSentenceEnvelopeRepository,
  )

  private val prisonerId = "ABC123"
  private val bookingId = 123456L
  private val calculationRequestRefence = UUID.randomUUID()
  private val calculationRequestId = 987654L
  private val prisonerDetails = PrisonerDetails(
    bookingId,
    prisonerId,
    "John",
    "Smith",
    LocalDate.of(1970, 1, 1),
  )

  @Test
  fun `should return issue if the prisoner is not found`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    assertThat(service.operativeSentenceEnvelopeForPrisoner(prisonerId)).isEqualTo("Prisoner ($prisonerId) could not be found".left())
  }

  @Test
  fun `should throw other unhandled exceptions loading prisoner details`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(500, "Boom", null, null, null))

    assertThrows<WebClientResponseException>("Boom") {
      service.operativeSentenceEnvelopeForPrisoner(prisonerId)
    }
  }

  @Test
  fun `should return a problem if could not load key dates from prison API`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    val expectedError = "Bang!"
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(expectedError.left())

    assertThat(service.operativeSentenceEnvelopeForPrisoner(prisonerId)).isEqualTo(expectedError.left())
  }

  @Test
  fun `should throw other unhandled exceptions loading prisoner key dates`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenThrow(WebClientResponseException(500, "Boom", null, null, null))

    assertThrows<WebClientResponseException>("Boom") {
      service.operativeSentenceEnvelopeForPrisoner(prisonerId)
    }
  }

  @Test
  fun `if there are no CRDS calcs then derive from NOMIS`() {
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = LocalDateTime.now(),
      calculatedByUserId = "username",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
      sentenceExpiryDate = LocalDate.of(2000, 1, 10),
    )
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(prisonService.getEarliestSentenceDate(bookingId)).thenReturn(LocalDate.of(2000, 1, 1))
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.empty())

    assertThat(service.operativeSentenceEnvelopeForPrisoner(prisonerId)).isEqualTo(
      OperativeSentenceEnvelope(
        sentenceEnvelopeLengthInDays = 10,
        earliestSentenceStartDate = LocalDate.of(2000, 1, 1),
        isPostRecallSentenceEnvelope = null,
        containsAnSDSPlusSentence = null,
        sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.NOMIS,
      ).right(),
    )
  }

  @Test
  fun `if deriving from NOMIS and there is no expiry date return an error`() {
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = LocalDateTime.now(),
      calculatedByUserId = "username",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
      sentenceExpiryDate = null,
    )
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(prisonService.getEarliestSentenceDate(bookingId)).thenReturn(LocalDate.of(2000, 1, 1))
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.empty())

    assertThat(service.operativeSentenceEnvelopeForPrisoner(prisonerId)).isEqualTo("Missing expiry date for booking (123456)".left())
  }

  @Test
  fun `if deriving from NOMIS and there is no sentence date return an error`() {
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = LocalDateTime.now(),
      calculatedByUserId = "username",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
      sentenceExpiryDate = LocalDate.of(2000, 1, 10),
    )
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(prisonService.getEarliestSentenceDate(bookingId)).thenReturn(null)
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.empty())

    assertThat(service.operativeSentenceEnvelopeForPrisoner(prisonerId)).isEqualTo("Missing sentence date for booking (123456)".left())
  }

  @Test
  fun `Should derive from NOMIS if the latest calculation is different to the CRDS one`() {
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = LocalDateTime.now(),
      calculatedByUserId = "username",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
      sentenceExpiryDate = LocalDate.of(2000, 1, 10),
    )
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(prisonService.getEarliestSentenceDate(bookingId)).thenReturn(LocalDate.of(2000, 1, 1))
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(CalculationRequest(calculationReference = UUID.randomUUID())))

    assertThat(service.operativeSentenceEnvelopeForPrisoner(prisonerId)).isEqualTo(
      OperativeSentenceEnvelope(
        sentenceEnvelopeLengthInDays = 10,
        earliestSentenceStartDate = LocalDate.of(2000, 1, 1),
        isPostRecallSentenceEnvelope = null,
        containsAnSDSPlusSentence = null,
        sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.NOMIS,
      ).right(),
    )

    verify(operativeSentenceEnvelopeRepository, never()).findByCalculationRequestId(calculationRequestId)
  }

  @Test
  fun `Should derive from NOMIS if the latest calculation is the same but it doesn't have a persisted operative sentence envelope`() {
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = LocalDateTime.now(),
      calculatedByUserId = "username",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
      sentenceExpiryDate = LocalDate.of(2000, 1, 10),
      comment = "This was a CRDS calc $calculationRequestRefence",
    )
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(prisonService.getEarliestSentenceDate(bookingId)).thenReturn(LocalDate.of(2000, 1, 1))
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(CalculationRequest(id = calculationRequestId, calculationReference = calculationRequestRefence)))
    whenever(operativeSentenceEnvelopeRepository.findByCalculationRequestId(calculationRequestId)).thenReturn(null)

    assertThat(service.operativeSentenceEnvelopeForPrisoner(prisonerId)).isEqualTo(
      OperativeSentenceEnvelope(
        sentenceEnvelopeLengthInDays = 10,
        earliestSentenceStartDate = LocalDate.of(2000, 1, 1),
        isPostRecallSentenceEnvelope = null,
        containsAnSDSPlusSentence = null,
        sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.NOMIS,
      ).right(),
    )

    verify(operativeSentenceEnvelopeRepository).findByCalculationRequestId(calculationRequestId)
  }

  @Test
  fun `Should use the persisted operative sentence envelope for a CRDS calc`() {
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = LocalDateTime.now(),
      calculatedByUserId = "username",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
      sentenceExpiryDate = LocalDate.of(2000, 1, 10),
      comment = "This was a CRDS calc $calculationRequestRefence",
    )
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(CalculationRequest(id = calculationRequestId, calculationReference = calculationRequestRefence)))
    whenever(operativeSentenceEnvelopeRepository.findByCalculationRequestId(calculationRequestId)).thenReturn(
      OperativeSentenceEnvelopeEntity(
        id = 99L,
        calculationRequestId = calculationRequestId,
        envelopeLengthDays = 25,
        earliestSentenceDate = LocalDate.of(2020, 1, 1),
        containsSdsPlusSentence = true,
        isPostRecall = false,
      ),
    )

    assertThat(service.operativeSentenceEnvelopeForPrisoner(prisonerId)).isEqualTo(
      OperativeSentenceEnvelope(
        sentenceEnvelopeLengthInDays = 25,
        earliestSentenceStartDate = LocalDate.of(2020, 1, 1),
        isPostRecallSentenceEnvelope = false,
        containsAnSDSPlusSentence = true,
        sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
      ).right(),
    )

    verify(operativeSentenceEnvelopeRepository).findByCalculationRequestId(calculationRequestId)
    verify(prisonService, never()).getEarliestSentenceDate(bookingId)
  }
}
