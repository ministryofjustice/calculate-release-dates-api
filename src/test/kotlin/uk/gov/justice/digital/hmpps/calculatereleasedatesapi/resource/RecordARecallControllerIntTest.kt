package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallSentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecision
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.remandandsentencing.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.ValidationIntTest.Companion.VALIDATION_PRISONER_ID
import java.time.LocalDate
import java.util.UUID

class RecordARecallControllerIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @MockitoSpyBean
  lateinit var featureToggles: FeatureToggles

  @Test
  fun `RCLL 583`() {
    mockManageOffencesClient.noneInPCSC(listOf("AR97001", "CE79046", "FI68002"))

    val result = createCalculationForRecordARecall(
      "RCLL-583",
      RecordARecallRequest(revocationDate = LocalDate.of(2025, 6, 1)),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
    assertThat(result.automatedCalculationData?.recallableSentences).hasSize(3)
    assertThat(result.automatedCalculationData?.expiredSentences).hasSize(0)
    assertThat(result.automatedCalculationData?.ineligibleSentences).hasSize(0)
    assertThat(result.automatedCalculationData?.sentencesBeforeInitialRelease).hasSize(0)
  }

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
    assertThat(result.automatedCalculationData.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.FTR_14, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_14, Recall.RecallType.FTR_HDC_28, Recall.RecallType.CUR_HDC, Recall.RecallType.IN_HDC))
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
  fun `Editing a recall excludes UAL from the recall being edited`() {
    val result = createCalculationForRecordARecall(
      RECORD_A_RECALL_PRISONER_ID,
      RecordARecallRequest(revocationDate = LocalDate.of(2016, 2, 6), recallId = UUID.fromString("e3c5888e-f8ad-48ea-8fc0-c916670f2ca1")),
    )

    assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
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

  @Nested
  inner class UnexpectedRecallTypeTests {
    @Test
    fun `Test unexpected recall types for SDS sentences under 12 months non HDC release`() {
      val result = createCalculationForRecordARecall(
        "RCLL-565-14",
        RecordARecallRequest(revocationDate = LocalDate.of(2024, 6, 1)),
      )

      assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
      assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.LR, Recall.RecallType.FTR_28, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_14, Recall.RecallType.FTR_HDC_28, Recall.RecallType.CUR_HDC, Recall.RecallType.IN_HDC))
    }

    @Test
    fun `Test unexpected recall types for SDS sentences over 12 months non HDC release`() {
      val result = createCalculationForRecordARecall(
        "RCLL-565-28",
        RecordARecallRequest(revocationDate = LocalDate.of(2024, 12, 1)),
      )

      assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
      assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.FTR_14, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_14, Recall.RecallType.FTR_HDC_28, Recall.RecallType.CUR_HDC, Recall.RecallType.IN_HDC))
    }

    @Test
    fun `Test unexpected recall types for SDS sentences under 12 months HDC release`() {
      val result = createCalculationForRecordARecall(
        "RCLL-565-H14",
        RecordARecallRequest(revocationDate = LocalDate.of(2024, 3, 2)),
      )

      assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
      assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.LR, Recall.RecallType.FTR_14, Recall.RecallType.FTR_28, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_28))
    }

    @Test
    fun `Test unexpected recall types for SDS sentences over 12 months HDC release`() {
      val result = createCalculationForRecordARecall(
        "RCLL-565-H28",
        RecordARecallRequest(revocationDate = LocalDate.of(2024, 5, 2)),
      )

      assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
      assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.LR, Recall.RecallType.FTR_14, Recall.RecallType.FTR_28, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_14))
    }

    @Test
    fun `Test unexpected recall types for SDS sentences under 12 months HDC release, revocation after CRD`() {
      val result = createCalculationForRecordARecall(
        "RCLL-565-H14",
        RecordARecallRequest(revocationDate = LocalDate.of(2024, 6, 2)),
      )

      assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
      assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.LR, Recall.RecallType.FTR_28, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_14, Recall.RecallType.FTR_HDC_28, Recall.RecallType.CUR_HDC, Recall.RecallType.IN_HDC))
    }

    @Test
    fun `Test unexpected recall types for SDS sentences over 12 months HDC release, revocation after CRD`() {
      val result = createCalculationForRecordARecall(
        "RCLL-565-H28",
        RecordARecallRequest(revocationDate = LocalDate.of(2024, 12, 1)),
      )

      assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
      assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.FTR_14, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_14, Recall.RecallType.FTR_HDC_28, Recall.RecallType.CUR_HDC, Recall.RecallType.IN_HDC))
    }

    @Nested
    inner class UnexpectedFtr56RecallTypes {
      @BeforeEach
      fun beforeEachFtr56Test() {
        whenever(featureToggles.recordARecallFtr56Rules).thenReturn(true)
      }

      @Test
      fun `Test unexpected recall types for SDS Adult sentences non HDC release`() {
        val result = createCalculationForRecordARecall(
          "RCLL-562-Adult",
          RecordARecallRequest(revocationDate = LocalDate.of(2024, 6, 1)),
        )

        assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
        assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.FTR_14, Recall.RecallType.FTR_28, Recall.RecallType.FTR_HDC_14, Recall.RecallType.FTR_HDC_28, Recall.RecallType.CUR_HDC, Recall.RecallType.IN_HDC))
      }

      @Test
      fun `Test unexpected recall types for SDS Youth sentences non HDC release`() {
        val result = createCalculationForRecordARecall(
          "RCLL-562-Youth",
          RecordARecallRequest(revocationDate = LocalDate.of(2024, 6, 1)),
        )

        assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
        assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.LR, Recall.RecallType.FTR_28, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_14, Recall.RecallType.FTR_HDC_28, Recall.RecallType.CUR_HDC, Recall.RecallType.IN_HDC))
      }

      @Test
      fun `Test unexpected recall types for SDS Adult sentences HDC release`() {
        val result = createCalculationForRecordARecall(
          "RCLL-562-Adult-HDC",
          RecordARecallRequest(revocationDate = LocalDate.of(2024, 3, 2)),
        )

        assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
        assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.LR, Recall.RecallType.FTR_14, Recall.RecallType.FTR_28, Recall.RecallType.FTR_HDC_14, Recall.RecallType.FTR_HDC_28))
      }

      @Test
      fun `Test unexpected recall types for SDS Youth sentences HDC release`() {
        val result = createCalculationForRecordARecall(
          "RCLL-562-Youth-HDC",
          RecordARecallRequest(revocationDate = LocalDate.of(2024, 3, 2)),
        )

        assertThat(result.decision).isEqualTo(RecordARecallDecision.AUTOMATED)
        assertThat(result.automatedCalculationData!!.unexpectedRecallTypes).isEqualTo(listOf(Recall.RecallType.LR, Recall.RecallType.FTR_14, Recall.RecallType.FTR_28, Recall.RecallType.FTR_56, Recall.RecallType.FTR_HDC_28))
      }
    }
  }

  companion object {
    const val RECORD_A_RECALL_PRISONER_ID = "RecARecall"
    const val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING = "RCLLBOO1"
    val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_NEW_BOOKING_ID = "RCLLBOO1".hashCode().toLong()
    val RECALL_PRISONER_WITH_SENTENCES_ON_OLDER_BOOKING_OLD_BOOKING_ID = "RCLLBOO2".hashCode().toLong()
  }
}
