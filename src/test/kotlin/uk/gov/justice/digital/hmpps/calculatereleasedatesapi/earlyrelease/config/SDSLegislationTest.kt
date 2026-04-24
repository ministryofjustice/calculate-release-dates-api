package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislation.ProgressionModelLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS

class SDSLegislationTest {

  @Nested
  inner class ProgressionModelTests {

    private val progressionModelLegislation = ProgressionModelLegislation(
      tranches = listOf(TrancheConfiguration(TrancheType.SENTENCE_LENGTH, LocalDate.of(2026, 9, 2), 100, ChronoUnit.DAYS, TrancheName.TRANCHE_1)),
      releaseMultiplier = mapOf(SentenceIdentificationTrack.SDS to ReleaseMultiplier.ONE_THIRD, SentenceIdentificationTrack.SDS_PLUS to ReleaseMultiplier.ONE_HALF),
    )

    @Test
    fun `SDS sentences should be subject tranches`() {
      val sentence = StandardDeterminateSentence(
        sentencedAt = LocalDate.of(2000, 1, 1),
        duration = FIVE_YEAR_DURATION,
        offence = Offence(
          committedAt = LocalDate.of(2000, 1, 1),
          offenceCode = "123",
        ),
        isSDSPlus = false,
        section250 = false,
        hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      sentence.identificationTrack = SentenceIdentificationTrack.SDS

      assertThat(progressionModelLegislation.isSentenceSubjectToTraches(sentence)).isTrue
    }

    @Test
    fun `SDS plus sentences should be subject tranches`() {
      val sentence = StandardDeterminateSentence(
        sentencedAt = LocalDate.of(2000, 1, 1),
        duration = FIVE_YEAR_DURATION,
        offence = Offence(
          committedAt = LocalDate.of(2000, 1, 1),
          offenceCode = "123",
        ),
        isSDSPlus = true,
        section250 = false,
        hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      sentence.identificationTrack = SentenceIdentificationTrack.SDS_PLUS

      assertThat(progressionModelLegislation.isSentenceSubjectToTraches(sentence)).isTrue
    }

    @Test
    fun `Section 250 sentences should not be subject tranches`() {
      val sentence = StandardDeterminateSentence(
        sentencedAt = LocalDate.of(2000, 1, 1),
        duration = FIVE_YEAR_DURATION,
        offence = Offence(
          committedAt = LocalDate.of(2000, 1, 1),
          offenceCode = "123",
        ),
        isSDSPlus = false,
        section250 = true,
        hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      sentence.identificationTrack = SentenceIdentificationTrack.SDS

      assertThat(progressionModelLegislation.isSentenceSubjectToTraches(sentence)).isFalse
    }

    @Test
    fun `Section 250 plus sentences should not be subject tranches`() {
      val sentence = StandardDeterminateSentence(
        sentencedAt = LocalDate.of(2000, 1, 1),
        duration = FIVE_YEAR_DURATION,
        offence = Offence(
          committedAt = LocalDate.of(2000, 1, 1),
          offenceCode = "123",
        ),
        isSDSPlus = true,
        section250 = true,
        hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      sentence.identificationTrack = SentenceIdentificationTrack.SDS_PLUS

      assertThat(progressionModelLegislation.isSentenceSubjectToTraches(sentence)).isFalse
    }

    @Test
    fun `Recall SDS sentences should not be subject tranches`() {
      val sentence = StandardDeterminateSentence(
        sentencedAt = LocalDate.of(2000, 1, 1),
        duration = FIVE_YEAR_DURATION,
        offence = Offence(
          committedAt = LocalDate.of(2000, 1, 1),
          offenceCode = "123",
        ),
        isSDSPlus = false,
        section250 = false,
        hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
        recall = Recall(RecallType.FIXED_TERM_RECALL_56),
      )
      sentence.identificationTrack = SentenceIdentificationTrack.SDS

      assertThat(progressionModelLegislation.isSentenceSubjectToTraches(sentence)).isFalse
    }

    @Test
    fun `EDS sentences should not be subject to tranches`() {
      val sentence = ExtendedDeterminateSentence(
        sentencedAt = LocalDate.of(2000, 1, 1),
        custodialDuration = FIVE_YEAR_DURATION,
        extensionDuration = FIVE_YEAR_DURATION,
        offence = Offence(
          committedAt = LocalDate.of(2000, 1, 1),
          offenceCode = "123",
        ),
        automaticRelease = false,
        caseReference = "ABC",
      )

      assertThat(progressionModelLegislation.isSentenceSubjectToTraches(sentence)).isFalse
    }

    @Test
    fun `SOPC sentences should not be subject to tranches`() {
      val sentence = SopcSentence(
        sentencedAt = LocalDate.of(2000, 1, 1),
        custodialDuration = FIVE_YEAR_DURATION,
        extensionDuration = FIVE_YEAR_DURATION,
        offence = Offence(
          committedAt = LocalDate.of(2000, 1, 1),
          offenceCode = "123",
        ),
        caseReference = "ABC",
      )

      assertThat(progressionModelLegislation.isSentenceSubjectToTraches(sentence)).isFalse
    }

    @Test
    fun `DTO sentences should not be subject to tranches`() {
      val sentence = DetentionAndTrainingOrderSentence(
        sentencedAt = LocalDate.of(2000, 1, 1),
        duration = FIVE_YEAR_DURATION,
        offence = Offence(
          committedAt = LocalDate.of(2000, 1, 1),
          offenceCode = "123",
        ),
      )

      assertThat(progressionModelLegislation.isSentenceSubjectToTraches(sentence)).isFalse
    }
  }

  companion object {
    private val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
  }
}
