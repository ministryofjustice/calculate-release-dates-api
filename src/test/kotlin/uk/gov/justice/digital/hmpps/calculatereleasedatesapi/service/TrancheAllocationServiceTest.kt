package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UnadjustedReleaseDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

class TrancheAllocationServiceTest {

  @Test
  fun `Single 5 year SDS eligible for SDS Early Release should be allocated to tranche 2`() {
    val testTrancheOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTrancheOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)
    val result = testTrancheAllocationService.calculateTranche(
      listOf(
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
          durationYears = 5L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusDays(10),
        ),
      ),
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_2)
  }

  @Test
  fun `Single 6 year SDS should be allocated to tranche 2`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)
    val result = testTrancheAllocationService.calculateTranche(
      listOf(
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
          durationYears = 6L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusDays(10),
        ),
      ),
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_2)
  }

  @Test
  fun `Single 4 year SDS not identified as early release track should be allocated to tranche 1`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)
    val result = testTrancheAllocationService.calculateTranche(
      listOf(
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE,
          durationYears = 4L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusDays(10),
        ),
      ),
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_1)
  }

  @Test
  fun `Recall time is discounted if SLED is before T1 commencement`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)
    val recallSentence =
      createRecallSentence(durationDays = 15, sentencedAt = testTranchOneCommencementDate.minusDays(14))
    recallSentence.sentenceCalculation = SentenceCalculation(
      UnadjustedReleaseDate(
        recallSentence,
        { 0.5 },
        { 0.5 },
      ),
      SentenceAdjustments(),
      false,
    )

    val sentences =
      listOf(
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
          durationYears = 4L,
          durationDays = 360L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusDays(1),
        ),
        recallSentence,

      )

    val result = testTrancheAllocationService.calculateTranche(
      sentences,
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_1)
  }

  @Test
  fun `5 year SDS with early release with a 4 year SDS not identified as early release track should be allocated to tranche 2`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)

    val result = testTrancheAllocationService.calculateTranche(
      listOf(
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE,
          durationYears = 4L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusYears(2),
        ),
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
          durationYears = 5L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusYears(1),
        ),
      ),
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_2)
  }

  @Test
  fun `6 year SDS with early release with a 4 year SDS not identified as early release track should be allocated to tranche 2`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)

    val result = testTrancheAllocationService.calculateTranche(
      listOf(
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE,
          durationYears = 4L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusYears(2),
        ),
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
          durationYears = 6L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusYears(1),
        ),
      ),
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_2)
  }

  @Test
  fun `Consecutive chain of less than 5 years gets tranche 1`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)

    val sentences = listOf(
      createBookingOfSDSSentencesOfTypeWithDuration(
        identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE,
        durationYears = 3L,
        sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusYears(1),
      ),
      createBookingOfSDSSentencesOfTypeWithDuration(
        identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
        durationYears = 1L,
        sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusDays(1),
      ),
    )

    val consecutiveSentence = listOf(createConsecutiveSentence(sentences))

    val result = testTrancheAllocationService.calculateTranche(consecutiveSentence)
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_1)
  }

  @Test
  fun `Consecutive chain of 5 years gets tranche 2`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)

    val sentences = listOf(
      createBookingOfSDSSentencesOfTypeWithDuration(
        identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE,
        durationYears = 3L,
        sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusYears(2),
      ),
      createBookingOfSDSSentencesOfTypeWithDuration(
        identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
        durationYears = 2L,
        sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusYears(1),
      ),
    )

    val consecutiveSentence = listOf(createConsecutiveSentence(sentences))

    val result = testTrancheAllocationService.calculateTranche(
      consecutiveSentence,
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_2)
  }

  @Test
  fun `Consecutive chain of 4 years 364 days gets tranche 1`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)

    val sentences = listOf(
      createBookingOfSDSSentencesOfTypeWithDuration(
        identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE,
        durationYears = 2L,
        durationDays = 364L,
        sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusYears(1),
      ),
      createBookingOfSDSSentencesOfTypeWithDuration(
        identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
        durationYears = 2L,
        sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusDays(1),
      ),
    )

    val consecutiveSentences = listOf(createConsecutiveSentence(sentences))

    val result = testTrancheAllocationService.calculateTranche(
      consecutiveSentences,
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_1)
  }

  @Test
  fun `SDS with early release track with 61 months gets tranche 2`() {
    val testTranchOneCommencementDate = LocalDate.of(2024, 9, 10)
    val testTrancheTwoCommencementDate = LocalDate.of(2024, 10, 22)
    val testTrancheThreeCommencementDate = LocalDate.of(2024, 12, 16)
    val trancheConfiguration = SDS40TrancheConfiguration(testTranchOneCommencementDate, testTrancheTwoCommencementDate, testTrancheThreeCommencementDate)
    val tranche = Tranche(trancheConfiguration)
    val testTrancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)

    val sentences =
      listOf(
        createBookingOfSDSSentencesOfTypeWithDuration(
          identificationTrack = SentenceIdentificationTrack.SDS_EARLY_RELEASE,
          durationMonths = 61L,
          sentencedAt = TRANCHE_ONE_COMMENCEMENT_DATE.minusDays(1),
        ),
      )

    val result = testTrancheAllocationService.calculateTranche(
      sentences,
    )
    assertThat(result).isEqualTo(SDSEarlyReleaseTranche.TRANCHE_2)
  }

  private fun createBookingOfSDSSentencesOfTypeWithDuration(
    identificationTrack: SentenceIdentificationTrack,
    durationYears: Long = 0L,
    durationMonths: Long = 0L,
    durationDays: Long = 0L,
    sentencedAt: LocalDate = LocalDate.of(2020, 1, 1),
  ): StandardDeterminateSentence {
    val sentence = StandardDeterminateSentence(
      sentencedAt = sentencedAt,
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to durationDays,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to durationMonths,
          ChronoUnit.YEARS to durationYears,
        ),
      ),
      offence = Offence(committedAt = LocalDate.of(2019, 1, 1)),
      identifier = UUID.randomUUID(),
      caseSequence = 1,
      lineSequence = 1,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    sentence.identificationTrack = identificationTrack
    sentence.releaseDateTypes = ReleaseDateTypes(listOf(ReleaseDateType.CRD, ReleaseDateType.SLED), sentence, offender = mock<Offender>())
    sentence.sentenceCalculation = SentenceCalculation(
      UnadjustedReleaseDate(
        sentence,
        { 0.5 },
        { 0.5 },
      ),
      SentenceAdjustments(),
      false,
    )

    return sentence
  }

  private fun createRecallSentence(
    durationYears: Long = 0L,
    durationMonths: Long = 0L,
    durationDays: Long = 0L,
    sentencedAt: LocalDate,
  ): StandardDeterminateSentence {
    val sentence = StandardDeterminateSentence(
      sentencedAt = sentencedAt,
      duration = Duration(
        mutableMapOf(
          ChronoUnit.DAYS to durationDays,
          ChronoUnit.WEEKS to 0L,
          ChronoUnit.MONTHS to durationMonths,
          ChronoUnit.YEARS to durationYears,
        ),
      ),
      offence = Offence(committedAt = LocalDate.of(2019, 1, 1)),
      identifier = UUID.randomUUID(),
      caseSequence = 1,
      lineSequence = 1,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      recallType = RecallType.STANDARD_RECALL,
    )
    sentence.identificationTrack = SentenceIdentificationTrack.RECALL
    sentence.releaseDateTypes = ReleaseDateTypes(listOf(ReleaseDateType.PRRD, ReleaseDateType.SLED), sentence, offender = mock<Offender>())

    return sentence
  }

  private fun createConsecutiveSentence(sentences: List<AbstractSentence>): ConsecutiveSentence {
    val consecutiveSentence = ConsecutiveSentence(sentences)
    consecutiveSentence.sentenceCalculation = SentenceCalculation(
      UnadjustedReleaseDate(
        consecutiveSentence,
        { 0.5 },
        { 0.5 },
      ),
      SentenceAdjustments(),
      false,
    )
    return consecutiveSentence
  }

  companion object {
    private val TRANCHE_ONE_COMMENCEMENT_DATE = LocalDate.of(2024, 9, 10)
    private val TRANCHE_TWO_COMMENCEMENT_DATE = LocalDate.of(2024, 10, 22)
  }
}
