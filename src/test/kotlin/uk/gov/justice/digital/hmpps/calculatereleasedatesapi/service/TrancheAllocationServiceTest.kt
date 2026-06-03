package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationWithTranches
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationWithTranches
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSTrancheSelectionStrategy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheSelectionStrategy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.TrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.ExternalMovementTimeline
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineFutureData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class TrancheAllocationServiceTest {

  private val service = TrancheAllocationService()

  private val timelineTrackingData = TimelineTrackingData(
    futureData = TimelineFutureData(
      remand = emptyList(),
      taggedBail = emptyList(),
      recallRemand = emptyList(),
      recallTaggedBail = emptyList(),
      additional = emptyList(),
      restored = emptyList(),
      ual = emptyList(),
      sentences = emptyList(),
    ),
    calculationsByDate = emptyMap(),
    latestRelease = SDS_SENTENCE.sentencedAt to SDS_SENTENCE,
    returnToCustodyDate = null,
    offender = Offender("AB1234C", LocalDate.of(1980, 1, 1)),
    options = CalculationOptions(calculateErsed = false),
    externalMovements = ExternalMovementTimeline(emptyList()),
  )

  @BeforeEach
  fun setUp() {
  }

  @Test
  fun `should return null for tranche when there are no sentences that might apply`() {
    val strategy = mock<TrancheSelectionStrategy>()
    val legislation = mock<LegislationWithTranches>()
    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(false)

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    assertThat(allocated).isNull()
  }

  @Test
  fun `should return null for SDS tranche already allocated`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()
    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    timelineTrackingData.applicableSdsLegislations.setApplicableLegislation(ApplicableLegislation(legislation, LocalDate.of(2020, 1, 1)))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    assertThat(allocated).isNull()
  }

  @Test
  fun `should return null for FTR tranche already allocated`() {
    val strategy = mock<TrancheSelectionStrategy>()
    val legislation = mock<FTRLegislation.FTR56Legislation>()
    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.FTR_56)
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    timelineTrackingData.applicableFtrLegislation = ApplicableLegislation(legislation, LocalDate.of(2020, 1, 1))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    assertThat(allocated).isNull()
  }

  @Test
  fun `should match tranche for SENTENCE_LENGTH_LESS_THAN for single sentence where length is equal to boundary`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 2, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val tranche3 = TrancheConfiguration(
      date = LocalDate.of(2020, 3, 1),
      name = TrancheName.TRANCHE_3,
      type = TrancheType.FINAL,
    )
    val sentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 100L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2, tranche3))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    // 100 days sentence falls into tranche 2 as SENTENCE_LENGTH_LESS_THAN is not inclusive of the upper bound
    assertThat(allocated).isEqualTo(tranche2)
  }

  @Test
  fun `should match tranche for SENTENCE_LENGTH_LESS_THAN for single sentence where length is one less than boundary`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 2, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val tranche3 = TrancheConfiguration(
      date = LocalDate.of(2020, 3, 1),
      name = TrancheName.TRANCHE_3,
      type = TrancheType.FINAL,
    )
    val sentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 99L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2, tranche3))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    // 99 days sentence falls into tranche 1 as SENTENCE_LENGTH_LESS_THAN is not inclusive of the upper bound
    assertThat(allocated).isEqualTo(tranche1)
  }

  @Test
  fun `should match tranche for SENTENCE_LENGTH_LESS_THAN for single sentence where length is one more than boundary`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 2, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val tranche3 = TrancheConfiguration(
      date = LocalDate.of(2020, 3, 1),
      name = TrancheName.TRANCHE_3,
      type = TrancheType.FINAL,
    )
    val sentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 101L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2, tranche3))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    assertThat(allocated).isEqualTo(tranche2)
  }

  @Test
  fun `should match tranche for SENTENCE_LENGTH_LESS_THAN for consecutive sentence`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 2, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val sentence = ConsecutiveSentence(
      listOf(
        SDS_SENTENCE.copy(
          duration = Duration(
            mutableMapOf(
              ChronoUnit.DAYS to 50L,
              ChronoUnit.WEEKS to 0L,
              ChronoUnit.MONTHS to 0L,
              ChronoUnit.YEARS to 0L,
            ),
          ),
        ),
        SDS_SENTENCE.copy(
          duration = Duration(
            mutableMapOf(
              ChronoUnit.DAYS to 50L,
              ChronoUnit.WEEKS to 0L,
              ChronoUnit.MONTHS to 0L,
              ChronoUnit.YEARS to 0L,
            ),
          ),
        ),
      ),
    )
    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    // 50 + 50 days sentence falls into tranche 2 as SENTENCE_LENGTH_LESS_THAN is not inclusive of the upper bound
    assertThat(allocated).isEqualTo(tranche2)
  }

  @Test
  fun `should match tranche for SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO for single sentence where length is equal to boundary`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 2, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val tranche3 = TrancheConfiguration(
      date = LocalDate.of(2020, 3, 1),
      name = TrancheName.TRANCHE_3,
      type = TrancheType.FINAL,
    )
    val sentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 100L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2, tranche3))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    // 100 days sentence falls into tranche 1 as SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO is inclusive of the upper bound
    assertThat(allocated).isEqualTo(tranche1)
  }

  @Test
  fun `should match tranche for SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO for single sentence where length is one less than boundary`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 2, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val tranche3 = TrancheConfiguration(
      date = LocalDate.of(2020, 3, 1),
      name = TrancheName.TRANCHE_3,
      type = TrancheType.FINAL,
    )
    val sentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 99L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2, tranche3))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    assertThat(allocated).isEqualTo(tranche1)
  }

  @Test
  fun `should match tranche for SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO for single sentence where length is one more than boundary`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 2, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val tranche3 = TrancheConfiguration(
      date = LocalDate.of(2020, 3, 1),
      name = TrancheName.TRANCHE_3,
      type = TrancheType.FINAL,
    )
    val sentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 101L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2, tranche3))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    assertThat(allocated).isEqualTo(tranche2)
  }

  @Test
  fun `should match tranche for SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO for consecutive sentence`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 2, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val sentence = ConsecutiveSentence(
      listOf(
        SDS_SENTENCE.copy(
          duration = Duration(
            mutableMapOf(
              ChronoUnit.DAYS to 50L,
              ChronoUnit.WEEKS to 0L,
              ChronoUnit.MONTHS to 0L,
              ChronoUnit.YEARS to 0L,
            ),
          ),
        ),
        SDS_SENTENCE.copy(
          duration = Duration(
            mutableMapOf(
              ChronoUnit.DAYS to 50L,
              ChronoUnit.WEEKS to 0L,
              ChronoUnit.MONTHS to 0L,
              ChronoUnit.YEARS to 0L,
            ),
          ),
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    // 50 + 50 days sentence falls into tranche 1 as SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO is inclusive of the upper bound
    assertThat(allocated).isEqualTo(tranche1)
  }

  @Test
  fun `should match tranche for FINAL_TRANCHE for single sentence`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val finalTranche = TrancheConfiguration(
      date = LocalDate.of(2020, 3, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.FINAL,
    )
    val sentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 999L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, finalTranche))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(sentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    assertThat(allocated).isEqualTo(finalTranche)
  }

  @Test
  fun `multiple sentences should allocate based on the longest duration`() {
    val strategy = mock<SDSTrancheSelectionStrategy>()
    val legislation = mock<SDSLegislationWithTranches>()

    val tranche1 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_1,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 100,
      unit = ChronoUnit.DAYS,
    )
    val tranche2 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_2,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 200,
      unit = ChronoUnit.DAYS,
    )
    val tranche3 = TrancheConfiguration(
      date = LocalDate.of(2020, 1, 1),
      name = TrancheName.TRANCHE_3,
      type = TrancheType.SENTENCE_LENGTH_LESS_THAN_OR_EQUAL_TO,
      duration = 300,
      unit = ChronoUnit.DAYS,
    )
    val shortSentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 50L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    val longerSentence = SDS_SENTENCE.copy(
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 150L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 0L,
        ),
      ),
    )

    whenever(legislation.trancheSelectionStrategy).thenReturn(strategy)
    whenever(legislation.legislationName).thenReturn(LegislationName.SDS_40)
    whenever(legislation.commencementDate()).thenReturn(LocalDate.of(2029, 1, 1))
    whenever(strategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, legislation)).thenReturn(true)
    whenever(legislation.tranches).thenReturn(listOf(tranche1, tranche2, tranche3))
    whenever(strategy.sentencesToMatchOnSentenceLength(timelineTrackingData, legislation)).thenReturn(listOf(shortSentence, longerSentence))

    val allocated = service.allocateTranche(timelineTrackingData, legislation)

    assertThat(allocated).isEqualTo(tranche2)
  }

  companion object {
    private val SDS_SENTENCE = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2000, 1, 1),
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to 0L,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to 0L,
          ChronoUnit.YEARS to 5L,
        ),
      ),
      offence = Offence(committedAt = LocalDate.of(2000, 1, 1)),
      identifier = UUID.randomUUID(),
      caseSequence = 1,
      lineSequence = 2,
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSection250 = false,
        sdsEarlyReleaseExclusions = emptyList(),
      ),
    )
  }
}
