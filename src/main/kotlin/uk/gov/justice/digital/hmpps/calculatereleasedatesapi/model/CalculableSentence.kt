package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.threeten.extra.LocalDateRange
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

  @JsonIgnore
  fun getRangeOfSentenceBeforeAwardedDays(): LocalDateRange {
    /*
    When we're walking the timeline of the sentence, we want to use the NPD date rather than PED, otherwise we
    could falsely state that there is a gap in the booking timeline if the prisoner wasn't released at the PED.
    */
    val releaseDate = if (getReleaseDateType() === ReleaseDateType.PED && this is StandardDeterminateSentence) {
      sentenceCalculation.nonParoleDate!!
    } else {
      sentenceCalculation.releaseDate
    }
    val releaseDateBeforeAda = releaseDate.minusDays(sentenceCalculation.calculatedTotalAwardedDays.toLong())

    return if (sentencedAt.isAfter(releaseDateBeforeAda)) {
      // The deducted days make this an immediate release sentence.
      LocalDateRange.of(
        sentencedAt,
        sentencedAt,
      )
    } else {
      LocalDateRange.of(
        sentencedAt,
        releaseDateBeforeAda,
      )
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun durationIsLessThan(length: Long, period: ChronoUnit): Boolean {
    return (
      getLengthInDays() <
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun durationIsLessThanEqualTo(length: Long, period: ChronoUnit): Boolean {
    return (
      getLengthInDays() <=
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun durationIsGreaterThan(length: Long, period: ChronoUnit): Boolean {
    return (
      getLengthInDays() >
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun durationIsGreaterThanOrEqualTo(length: Long, period: ChronoUnit): Boolean {
    return (
      getLengthInDays() >=
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  @JsonIgnore
  fun getHalfSentenceDate(): LocalDate {
    val days = (getLengthInDays().toDouble() / 2).roundToLong()
    return this.sentencedAt.plusDays(days)
  }

  @JsonIgnore
  fun getLengthInDays(): Int

  @JsonIgnore
  fun isRecall(): Boolean {
    return recallType != null
  }

  @JsonIgnore
  fun hasAnyEdsOrSopcSentence(): Boolean

  @JsonIgnore
  fun getReleaseDateType(): ReleaseDateType {
    return if (isRecall()) {
      ReleaseDateType.PRRD
    } else if (releaseDateTypes.getReleaseDateTypes().contains(ReleaseDateType.PED) && this.sentenceCalculation.extendedDeterminateParoleEligibilityDate == null) {
      ReleaseDateType.PED
    } else if (sentenceCalculation.isReleaseDateConditional) {
      ReleaseDateType.CRD
    } else if (releaseDateTypes.contains(ReleaseDateType.MTD)) {
      ReleaseDateType.MTD
    } else {
      ReleaseDateType.ARD
    }
  }

  @JsonIgnore
  fun totalDuration(): Duration {
    return when (this) {
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

      else -> {
        throw UnknownError("Unknown sentence")
      }
    }
  }

  @JsonIgnore
  fun custodialDuration(): Duration {
    return when (this) {
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

      else -> {
        throw UnknownError("Unknown sentence")
      }
    }
  }

  fun calculateErsedFromHalfway(): Boolean
  fun calculateErsedFromTwoThirds(): Boolean
  fun buildString(): String

  @JsonIgnore
  fun isCalculationInitialised(): Boolean

  @JsonIgnore
  fun isIdentificationTrackInitialized(): Boolean

  @JsonIgnore
  fun isDto(): Boolean {
    return this is DetentionAndTrainingOrderSentence ||
      (this is ConsecutiveSentence && this.orderedSentences.all { it is DetentionAndTrainingOrderSentence }) ||
      this is DtoSingleTermSentence
  }
}
