package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * This interface is used for any sentence that can be used to calculated release dates.
 * Each sentence will go through three stages
 * 1. Identify, figure out which laws apply to each sentence and which release dates they will result in.
 * 2. Calculate, calculate the release dates for each sentence
 * 3. Extract, extract the final booking release dates from all the calculable sentences.
 */
interface CalculableSentence {
  var releaseDateTypes: ReleaseDateTypes
  var sentenceCalculation: SentenceCalculation
  val recallType: RecallType?
  val sentencedAt: LocalDate
  val offence: Offence
  var identificationTrack: SentenceIdentificationTrack
  val isSDSPlus: Boolean
  val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean
  val isSDSPlusOffenceInPeriod: Boolean

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun durationIsLessThan(length: Long, period: ChronoUnit): Boolean = (
    getLengthInDays() <
      ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
    )

  fun durationIsLessThanEqualTo(length: Long, period: ChronoUnit): Boolean = (
    getLengthInDays() <=
      ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
    )

  fun durationIsGreaterThan(length: Long, period: ChronoUnit): Boolean = (
    getLengthInDays() >
      ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
    )

  fun durationIsGreaterThanOrEqualTo(length: Long, period: ChronoUnit): Boolean = (
    getLengthInDays() >=
      ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
    )

  @JsonIgnore
  fun getHalfSentenceDate(): LocalDate {
    val days = (getLengthInDays().toDouble() / 2).roundToLong()
    return this.sentencedAt.plusDays(days)
  }

  @JsonIgnore
  fun getLengthInDays(): Int

  @JsonIgnore
  fun isRecall(): Boolean = recallType != null

  @JsonIgnore
  fun isOrExclusivelyBotus(): Boolean = false

  @JsonIgnore
  fun hasAnyEdsOrSopcSentence(): Boolean

  @JsonIgnore
  fun getReleaseDateType(): ReleaseDateType = when {
    isRecall() -> {
      ReleaseDateType.PRRD
    }
    releaseDateTypes.getReleaseDateTypes().contains(ReleaseDateType.PED) && this.sentenceCalculation.extendedDeterminateParoleEligibilityDate == null -> {
      ReleaseDateType.PED
    }
    sentenceCalculation.isReleaseDateConditional -> {
      ReleaseDateType.CRD
    }
    releaseDateTypes.contains(ReleaseDateType.MTD) -> {
      ReleaseDateType.MTD
    }
    else -> {
      ReleaseDateType.ARD
    }
  }

  @JsonIgnore
  fun totalDuration(): Duration = when (this) {
    is StandardDeterminateSentence -> {
      this.duration
    }

    is AFineSentence -> {
      this.duration
    }

    is ExtendedDeterminateSentence -> {
      this.combinedDuration()
    }

    is SopcSentence -> {
      this.combinedDuration()
    }

    is SingleTermSentence -> {
      this.combinedDuration()
    }

    is DetentionAndTrainingOrderSentence -> {
      this.duration
    }

    is BotusSentence -> {
      this.duration
    }

    else -> {
      throw UnknownError("Unknown sentence")
    }
  }

  @JsonIgnore
  fun custodialDuration(): Duration = when (this) {
    is StandardDeterminateSentence -> {
      this.duration
    }

    is AFineSentence -> {
      this.duration
    }

    is ExtendedDeterminateSentence -> {
      this.custodialDuration
    }

    is SopcSentence -> {
      this.custodialDuration
    }

    is SingleTermSentence -> {
      this.combinedDuration()
    }

    is DtoSingleTermSentence -> {
      this.combinedDuration()
    }

    is DetentionAndTrainingOrderSentence -> {
      this.duration
    }
    is BotusSentence -> {
      this.duration
    }

    else -> {
      throw UnknownError("Unknown sentence")
    }
  }

  fun calculateErsed(): Boolean
  fun buildString(): String

  @JsonIgnore
  fun isCalculationInitialised(): Boolean

  @JsonIgnore
  fun isIdentificationTrackInitialized(): Boolean

  @JsonIgnore
  fun isDto(): Boolean = this is DetentionAndTrainingOrderSentence ||
    (this is ConsecutiveSentence && this.orderedSentences.all { it is DetentionAndTrainingOrderSentence }) ||
    this is DtoSingleTermSentence

  @JsonIgnore
  fun sentenceParts(): List<AbstractSentence>

  @JsonIgnore
  fun isAffectedBySds40EarlyRelease(): Boolean = this.sentenceParts().any {
    it.identificationTrack == SentenceIdentificationTrack.SDS &&
      this.sentenceCalculation.unadjustedReleaseDate.findMultiplierBySentence(it) == 0.4
  }
}
