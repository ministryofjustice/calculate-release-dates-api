package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.core.type.TypeReference
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestSentenceRepository
import java.time.LocalDate

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["feature-toggles.use-adjustments-api=true", "feature-toggles.store-sentence-level-dates=true", "feature-toggles.apply-post-recall-repeal-rules=false"])
@Sql(scripts = ["classpath:/test_data/reset-base-data.sql"])
class SentenceLevelDatesAreStoredAlongsideACalculationIntTest(private val mockPrisonService: MockPrisonService, private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestSentenceRepository: CalculationRequestSentenceRepository

  @Autowired
  lateinit var calculationRequestSentenceOutcomeRepository: CalculationRequestSentenceOutcomeRepository

  @BeforeEach
  fun setUp() {
    mockPrisonService.withInstAgencies(
      listOf(
        Agency("ABC", "prison ABC"),
        Agency("HDC4P", "prison HDC4P"),
      ),
    )
    mockPrisonService.withNomisCalculationReasons(
      listOf(
        NomisCalculationReason("NEW", "New Sentence"),
      ),
    )
    mockManageOffencesClient.noneInPCSC(listOf("AR97001", "CE79046", "FI68002"))
  }

  @Test
  fun `Stores sentence level dates`() {
    val calc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertThat(calc.dates[SLED]).isEqualTo(LocalDate.of(2025, 11, 30))
    assertThat(calc.dates[CRD]).isEqualTo(LocalDate.of(2024, 9, 14))
    assertThat(calc.dates[HDCED]).isEqualTo(LocalDate.of(2024, 5, 31))

    val sentences = calculationRequestSentenceRepository.findByCalculationRequestId(calc.calculationRequestId)

    assertThat(sentences).hasSize(3)
    val readerForSentences = objectMapper.readerFor(SentenceAndOffenceWithReleaseArrangements::class.java)
    val readerForAdjustments = objectMapper.readerFor(object : TypeReference<List<AdjustmentDto>>() {})
    val withExtractedSentencesAndAdjustments = sentences.map {
      val sentence = readerForSentences.readValue<SentenceAndOffenceWithReleaseArrangements>(it.inputSentenceData)
      val adjustments = readerForAdjustments.readValue<List<AdjustmentDto>>(it.sentenceAdjustments)
      val sentenceAndAdjustments = sentence to adjustments
      it to sentenceAndAdjustments
    }

    val (firstEntity, firstSentenceAndAdjustments) = withExtractedSentencesAndAdjustments.find { it.second.first.sentenceSequence == 1 }!!
    assertThat(firstSentenceAndAdjustments.second).hasSize(1)
    assertThat(firstEntity.impactsFinalReleaseDate).isTrue
    assertThat(firstEntity.releaseMultiplier).isEqualTo(0.5, within(0.01))
    val firstDates = calculationRequestSentenceOutcomeRepository.findByCalculationRequestSentenceId(firstEntity.id!!)
    assertThat(firstDates).hasSize(3)
    assertThat(firstDates.find { it.calculationDateType == SLED }?.outcomeDate).isEqualTo(LocalDate.of(2025, 11, 30))
    assertThat(firstDates.find { it.calculationDateType == CRD }?.outcomeDate).isEqualTo(LocalDate.of(2024, 9, 14))
    assertThat(firstDates.find { it.calculationDateType == HDCED }?.outcomeDate).isEqualTo(LocalDate.of(2024, 4, 20))

    val (secondEntity, secondSentenceAndAdjustments) = withExtractedSentencesAndAdjustments.find { it.second.first.sentenceSequence == 2 }!!
    assertThat(secondSentenceAndAdjustments.second).hasSize(1)
    assertThat(secondEntity.impactsFinalReleaseDate).isTrue
    assertThat(secondEntity.releaseMultiplier).isEqualTo(0.5, within(0.01))
    val secondDates = calculationRequestSentenceOutcomeRepository.findByCalculationRequestSentenceId(secondEntity.id!!)
    assertThat(secondDates).hasSize(3)
    assertThat(secondDates.find { it.calculationDateType == SLED }?.outcomeDate).isEqualTo(LocalDate.of(2025, 11, 30))
    assertThat(secondDates.find { it.calculationDateType == CRD }?.outcomeDate).isEqualTo(LocalDate.of(2024, 9, 14))
    assertThat(secondDates.find { it.calculationDateType == HDCED }?.outcomeDate).isEqualTo(LocalDate.of(2024, 4, 20))

    val (thirdEntity, thirdSentenceAndAdjustments) = withExtractedSentencesAndAdjustments.find { it.second.first.sentenceSequence == 3 }!!
    assertThat(thirdSentenceAndAdjustments.second).isEmpty()
    assertThat(thirdEntity.impactsFinalReleaseDate).isFalse
    assertThat(thirdEntity.releaseMultiplier).isEqualTo(0.5, within(0.01))
    val thirdDates = calculationRequestSentenceOutcomeRepository.findByCalculationRequestSentenceId(thirdEntity.id!!)
    assertThat(thirdDates).hasSize(3)
    assertThat(thirdDates.find { it.calculationDateType == SLED }?.outcomeDate).isEqualTo(LocalDate.of(2024, 1, 1))
    assertThat(thirdDates.find { it.calculationDateType == CRD }?.outcomeDate).isEqualTo(LocalDate.of(2024, 1, 1))
    assertThat(thirdDates.find { it.calculationDateType == TUSED }?.outcomeDate).isEqualTo(LocalDate.of(2025, 1, 1))
    assertThat(thirdDates.find { it.calculationDateType == HDCED }?.outcomeDate).isNull()
  }

  companion object {
    private const val PRISONER_ID = "SLD01"

    private const val INITIAL_CALC_REASON_ID = 1L
  }
}
