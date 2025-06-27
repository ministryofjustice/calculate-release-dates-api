package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.github.tomakehurst.wiremock.client.WireMock.get
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.ValidationIntTest.Companion.VALIDATION_PRISONER_ID
import java.time.LocalDate

class RecordARecallControllerIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  // TODO setup a decent test for this. WIth sled and sentences from old booking.
  // TODO test adjustments from old bookings link up correctly
  // TODO adjustments API filter on booking id.

  @Test
  fun `Run calculation using the record-a-recall endpoint for a prisoner (based on example 13 from the unit tests)`() {
    val result = createCalculationForRecordARecall(CalculationIntTest.PRISONER_ID)
    assertThat(result.validationMessages).isEmpty()

    val calculationResult = result.calculatedReleaseDates!!

    val calculationRequest = calculationRequestRepository.findById(calculationResult.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists for id ${calculationResult.calculationRequestId}") }

    assertThat(calculationRequest.calculationStatus).isEqualTo("RECORD_A_RECALL")
    assertThat(calculationResult.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(calculationResult.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
    assertThat(calculationResult.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
    assertThat(calculationResult.dates[HDCED]).isEqualTo(LocalDate.of(2015, 8, 7))
    assertThat(calculationResult.dates[ESED]).isEqualTo(LocalDate.of(2016, 11, 16))
    assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(CalculationIntTest.PRISONER_ID)
    assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText())
      .isEqualTo("2015-03-17")
  }

  @Test
  fun `Run calculation using the record-a-recall endpoint for a prisoner with validation failures`() {
    mockManageOffencesClient.noneInPCSC(listOf("GBH", "SX03014"))
    val result = createCalculationForRecordARecall(VALIDATION_PRISONER_ID)

    assertThat(result.validationMessages).isNotEmpty()
    assertThat(result.calculatedReleaseDates).isNull()
  }

  @Test
  fun `Run calculation using the record-a-recall endpoint for a prisoner sentences on a previous booking`() {
    val result = createCalculationForRecordARecall(RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING)

    assertThat(result.validationMessages).isEmpty()

    val sentencesAndOffences = getSentencesAndOffencesForCalculation(result.calculatedReleaseDates!!.calculationRequestId)
    val bookingIds = sentencesAndOffences.map { it.bookingId }.distinct()
    assertThat(bookingIds).hasSize(2).contains(RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_NEW_BOOKING_ID, RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_OLD_BOOKING_ID)
  }

  companion object {
    const val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING = "RCLLBOO1"
    val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_NEW_BOOKING_ID = "RCLLBOO1".hashCode().toLong()
    val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_OLD_BOOKING_ID = "RCLLBOO2".hashCode().toLong()
  }
}
