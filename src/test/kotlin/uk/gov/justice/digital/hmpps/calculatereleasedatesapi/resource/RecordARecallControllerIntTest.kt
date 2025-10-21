package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallSentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecision
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.ValidationIntTest.Companion.VALIDATION_PRISONER_ID
import java.time.LocalDate
import java.util.UUID

class RecordARecallControllerIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  fun `Find sentences that have license periods for revocation date`() {
    val result = createCalculationForRecordARecall(
      CalculationIntTest.PRISONER_ID,
      RecordARecallRequest(revocationDate = LocalDate.of(2016, 3, 6), arrestDate = LocalDate.of(2016, 3, 10)),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
    assertThat(result.validationMessages).isEmpty()
    assertThat(result.recallableSentences).hasSize(1)
    assertThat(result.recallableSentences[0]).isEqualTo(
      RecallableSentence(
        sentenceSequence = 1,
        bookingId = CalculationIntTest.PRISONER_ID.hashCode().toLong(),
        uuid = UUID.fromString("0e1fdbd3-f296-3c0e-8c0e-97bad6fc1509"),
        sentenceCalculation = RecallSentenceCalculation(
          conditionalReleaseDate = LocalDate.of(2016, 1, 6),
          actualReleaseDate = LocalDate.of(2016, 1, 6),
          licenseExpiry = LocalDate.of(2016, 11, 6),
        ),
      ),
    )
  }

  @Test
  fun `No sentences for recall`() {
    val result = createCalculationForRecordARecall(
      CalculationIntTest.PRISONER_ID,
      RecordARecallRequest(revocationDate = LocalDate.of(2022, 2, 6)),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.NO_RECALLABLE_SENTENCES_FOUND)
  }

  @Test
  fun `Conflicting UAL`() {
    val result = createCalculationForRecordARecall(
      CalculationIntTest.PRISONER_ID,
      RecordARecallRequest(revocationDate = LocalDate.of(2016, 2, 6), arrestDate = LocalDate.of(2016, 2, 10)),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.CONFLICTING_ADJUSTMENTS)
  }

  @Test
  fun `Validation errors`() {
    mockManageOffencesClient.noneInPCSC(listOf("GBH", "SX03014"))
    val result = createCalculationForRecordARecall(
      VALIDATION_PRISONER_ID,
      RecordARecallRequest(revocationDate = LocalDate.of(2016, 2, 6)),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.CRITICAL_ERRORS)
    assertThat(result.validationMessages).isNotEmpty()
  }

  @Test
  fun `Calculation across multiple bookings`() {
    val result = createCalculationForRecordARecall(RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING, RecordARecallRequest(revocationDate = LocalDate.of(2025, 7, 6)))

    assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
    assertThat(result.validationMessages).isEmpty()
    assertThat(result.recallableSentences).hasSize(2)
    val bookingIds = result.recallableSentences.map { it.bookingId }.distinct()
    assertThat(bookingIds).hasSize(2).contains(RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_NEW_BOOKING_ID, RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_OLD_BOOKING_ID)
  }

  companion object {
    const val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING = "RCLLBOO1"
    val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_NEW_BOOKING_ID = "RCLLBOO1".hashCode().toLong()
    val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_OLD_BOOKING_ID = "RCLLBOO2".hashCode().toLong()
  }
}
