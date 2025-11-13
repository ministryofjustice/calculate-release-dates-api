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
      RECORD_A_RECALL_PRISONER_ID,
      RecordARecallRequest(revocationDate = LocalDate.of(2016, 3, 6)),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
    assertThat(result.validationMessages).isEmpty()
    assertThat(result.automatedCalculationData!!.recallableSentences).hasSize(1)
    assertThat(result.automatedCalculationData.recallableSentences[0]).isEqualTo(
      RecallableSentence(
        sentenceSequence = 1,
        bookingId = RECORD_A_RECALL_PRISONER_ID.hashCode().toLong(),
        uuid = UUID.fromString("877e7ee9-77e5-3fe4-afd1-bef67d166a36"),
        sentenceCalculation = RecallSentenceCalculation(
          conditionalReleaseDate = LocalDate.of(2016, 1, 6),
          actualReleaseDate = LocalDate.of(2016, 1, 6),
          licenseExpiry = LocalDate.of(2016, 11, 6),
        ),
      ),
    )
    assertThat(result.automatedCalculationData.ineligibleSentences[0]).isEqualTo(
      RecallableSentence(
        sentenceSequence = 2,
        bookingId = RECORD_A_RECALL_PRISONER_ID.hashCode().toLong(),
        uuid = UUID.fromString("1105c0c1-3d63-3ac1-88f1-7d13caa56d7f"),
        sentenceCalculation = RecallSentenceCalculation(
          conditionalReleaseDate = LocalDate.of(2015, 4, 6),
          actualReleaseDate = LocalDate.of(2016, 1, 6),
          licenseExpiry = null,
        ),
      ),
    )
    assertThat(result.automatedCalculationData.expiredSentences[0]).isEqualTo(
      RecallableSentence(
        sentenceSequence = 3,
        bookingId = RECORD_A_RECALL_PRISONER_ID.hashCode().toLong(),
        uuid = UUID.fromString("5dd9de80-360f-3e51-8483-9ab02ee2d4b9"),
        sentenceCalculation = RecallSentenceCalculation(
          conditionalReleaseDate = LocalDate.of(2014, 7, 2),
          actualReleaseDate = LocalDate.of(2014, 7, 2),
          licenseExpiry = LocalDate.of(2014, 12, 31),
        ),
      ),
    )
    assertThat(result.automatedCalculationData.sentencesBeforeInitialRelease[0]).isEqualTo(
      RecallableSentence(
        sentenceSequence = 4,
        bookingId = RECORD_A_RECALL_PRISONER_ID.hashCode().toLong(),
        uuid = UUID.fromString("70b16cf7-0c1e-3c40-9f66-4c20115ce5fc"),
        sentenceCalculation = RecallSentenceCalculation(
          conditionalReleaseDate = LocalDate.of(2016, 8, 30),
          actualReleaseDate = LocalDate.of(2016, 8, 30),
          licenseExpiry = LocalDate.of(2017, 2, 28),
        ),
      ),
    )
  }

  @Test
  fun `Validation passes`() {
    mockManageOffencesClient.noneInPCSC(listOf("GBH", "SX03014"))
    val result = validateForRecordARecall(
      RECORD_A_RECALL_PRISONER_ID,
    )

    assertThat(result.criticalValidationMessages).isEmpty()
    assertThat(result.otherValidationMessages).isEmpty()
  }

  @Test
  fun `No sentences for recall`() {
    val result = createCalculationForRecordARecall(
      RECORD_A_RECALL_PRISONER_ID,
      RecordARecallRequest(revocationDate = LocalDate.of(2022, 2, 6)),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.NO_RECALLABLE_SENTENCES_FOUND)
  }

  @Test
  fun `Conflicting UAL`() {
    val result = createCalculationForRecordARecall(
      RECORD_A_RECALL_PRISONER_ID,
      RecordARecallRequest(revocationDate = LocalDate.of(2016, 2, 6)),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.CONFLICTING_ADJUSTMENTS)
    assertThat(result.conflictingAdjustments).isEqualTo(listOf("2edb6550-ff6a-43e7-b563-d7e447b6a8be"))
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
  fun `Validation errors from validation endpoint`() {
    mockManageOffencesClient.noneInPCSC(listOf("GBH", "SX03014"))
    val result = validateForRecordARecall(
      VALIDATION_PRISONER_ID,
    )

    assertThat(result.criticalValidationMessages).isNotEmpty()
    assertThat(result.otherValidationMessages).isNotEmpty()
  }

  @Test
  fun `Calculation across multiple bookings`() {
    val result = createCalculationForRecordARecall(RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING, RecordARecallRequest(revocationDate = LocalDate.of(2025, 7, 6)))

    assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
    assertThat(result.validationMessages).isEmpty()
    assertThat(result.automatedCalculationData!!.recallableSentences).hasSize(2)
    val bookingIds = result.automatedCalculationData.recallableSentences.map { it.bookingId }.distinct()
    assertThat(bookingIds).hasSize(2).contains(RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_NEW_BOOKING_ID, RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_OLD_BOOKING_ID)
  }

  companion object {
    const val RECORD_A_RECALL_PRISONER_ID = "RecARecall"
    const val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING = "RCLLBOO1"
    val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_NEW_BOOKING_ID = "RCLLBOO1".hashCode().toLong()
    val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_OLD_BOOKING_ID = "RCLLBOO2".hashCode().toLong()
  }
}
