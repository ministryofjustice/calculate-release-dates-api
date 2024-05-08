package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Service
class SentenceCalculationService(
  private val sentenceAdjustedCalculationService: SentenceAdjustedCalculationService,
  private val releasePointMultiplierLookup: ReleasePointMultiplierLookup,
) {

  fun calculate(sentence: CalculableSentence, booking: Booking): SentenceCalculation {
    // create association between the sentence and it's calculation
    sentence.sentenceCalculation = getSentenceCalculation(booking, sentence)
    return sentenceAdjustedCalculationService.calculateDatesFromAdjustments(sentence, booking)
  }

  private fun getConsecutiveRelease(sentence: ConsecutiveSentence): ReleaseDateCalculation {
    val daysToExpiry = getDaysInGroup(sentence.orderedSentences, sentence.sentencedAt) { it.totalDuration() }
    val sentencesWithPed =
      sentence.orderedSentences.filter { (it is ExtendedDeterminateSentence && !it.automaticRelease) || it is SopcSentence }
    val sentencesTwoThirdsWithoutPed =
      sentence.orderedSentences.filter { !sentencesWithPed.contains(it) && ((it is StandardDeterminateSentence && it.isSDSPlus) || (it is ExtendedDeterminateSentence && it.automaticRelease)) }
    val sentencesHalfwayWithoutPed = sentence.orderedSentences.filter {
      !sentencesWithPed.contains(it) && !sentencesTwoThirdsWithoutPed.contains(it)
    }

    val firstIndexOfNonPedTwoThirds =
      sentencesTwoThirdsWithoutPed.minOfOrNull { sentence.orderedSentences.indexOf(it) }
    val firstIndexOfNonPedHalfway = sentencesHalfwayWithoutPed.minOfOrNull { sentence.orderedSentences.indexOf(it) }
    val sentencesInCalculationOrder =
      if (firstIndexOfNonPedTwoThirds != null && firstIndexOfNonPedHalfway != null && firstIndexOfNonPedTwoThirds < firstIndexOfNonPedHalfway) {
        listOf(
          sentencesTwoThirdsWithoutPed,
          sentencesHalfwayWithoutPed,
          sentencesWithPed,
        )
      } else {
        listOf(sentencesHalfwayWithoutPed, sentencesTwoThirdsWithoutPed, sentencesWithPed)
      }

    var notionalCrd: LocalDate? = null
    var daysToRelease = 0
    var numberOfDaysToParoleEligibilityDate: Long? = null
    sentencesInCalculationOrder.forEach {
      if (it.isNotEmpty()) {
        val releaseStartDate = if (notionalCrd != null) notionalCrd!!.plusDays(1) else sentence.sentencedAt
        val daysInThisCustodialDuration = getDaysInGroup(it, releaseStartDate) { it.custodialDuration() }
        if (it == sentencesWithPed && !sentence.isRecall()) {
          numberOfDaysToParoleEligibilityDate = calculateConsecutivePed(it, daysToRelease, releaseStartDate)
        }
        val multiplier = releasePointMultiplierLookup.multiplierFor(it[0].identificationTrack)
        val daysToReleaseInThisGroup = ceil(daysInThisCustodialDuration.toDouble().times(multiplier)).toLong()
        notionalCrd = releaseStartDate
          .plusDays(daysToReleaseInThisGroup)
          .minusDays(1)
        daysToRelease += daysToReleaseInThisGroup.toInt()
      }
    }

    if (sentence.isRecall()) {
      numberOfDaysToParoleEligibilityDate = null
    }

    return ReleaseDateCalculation(
      daysToExpiry,
      daysToRelease.toDouble(),
      daysToRelease,
      numberOfDaysToParoleEligibilityDate,
    )
  }

  private fun calculateConsecutivePed(calculableSentences: List<CalculableSentence>, daysToRelease: Int, releaseStartDate: LocalDate): Long {
    val firstSentence = calculableSentences[0]
    val firstSentenceMultiplier = determinePedMultiplier(firstSentence.identificationTrack)
    val sentencesOfFirstType = calculableSentences.filter { determinePedMultiplier(it.identificationTrack) == firstSentenceMultiplier }
    val sentencesOfOtherType = calculableSentences.filter { determinePedMultiplier(it.identificationTrack) != firstSentenceMultiplier }

    val daysInFirstCustodialDuration = getDaysInGroup(sentencesOfFirstType, releaseStartDate) { it.custodialDuration() }
    var daysToPed = ceil(daysInFirstCustodialDuration.times(firstSentenceMultiplier)).toLong()
    val notionalPed = releaseStartDate
      .plusDays(daysToPed)
      .minusDays(1)
    if (sentencesOfOtherType.isNotEmpty()) {
      val otherSentenceMultiplier = determinePedMultiplier(sentencesOfOtherType[0].identificationTrack)
      val daysInOtherCustodialDuration = getDaysInGroup(sentencesOfOtherType, notionalPed) { it.custodialDuration() }
      daysToPed += ceil(daysInOtherCustodialDuration.times(otherSentenceMultiplier)).toLong()
    }
    return daysToRelease + daysToPed
  }

  private fun getDaysInGroup(sentences: List<CalculableSentence>, sentenceStartDate: LocalDate, durationSupplier: (sentence: CalculableSentence) -> Duration): Int {
    if (sentences.all { it.isDto() }) {
      val days = ConsecutiveSentenceAggregator(sentences.map(durationSupplier)).calculateDays(sentenceStartDate)
      val between = ChronoUnit.DAYS.between(sentenceStartDate, sentenceStartDate.plusMonths(24))
      return if (days >= between) {
        between.toInt()
      } else {
        days
      }
    }
    return ConsecutiveSentenceAggregator(sentences.map(durationSupplier)).calculateDays(sentenceStartDate)
  }

  private fun getSentenceCalculation(booking: Booking, sentence: CalculableSentence): SentenceCalculation {
    val release = if (sentence is ConsecutiveSentence) {
      getConsecutiveRelease(sentence)
    } else {
      getSingleSentenceRelease(sentence)
    }
    val unadjustedExpiryDate =
      sentence.sentencedAt
        .plusDays(release.numberOfDaysToSentenceExpiryDate.toLong())
        .minusDays(1)

    val unadjustedDeterminateReleaseDate =
      sentence.sentencedAt
        .plusDays(release.numberOfDaysToDeterminateReleaseDate.toLong())
        .minusDays(1)

    var numberOfDaysToPostRecallReleaseDate: Int? = null
    var unadjustedPostRecallReleaseDate: LocalDate? = null
    if (sentence.isRecall()) {
      if (sentence.recallType == RecallType.STANDARD_RECALL) {
        numberOfDaysToPostRecallReleaseDate = release.numberOfDaysToSentenceExpiryDate
        unadjustedPostRecallReleaseDate = unadjustedExpiryDate
      } else if (sentence.recallType == RecallType.FIXED_TERM_RECALL_14) {
        numberOfDaysToPostRecallReleaseDate = 14
        unadjustedPostRecallReleaseDate = calculateFixedTermRecall(booking, 14)
      } else if (sentence.recallType == RecallType.FIXED_TERM_RECALL_28) {
        numberOfDaysToPostRecallReleaseDate = 28
        unadjustedPostRecallReleaseDate = calculateFixedTermRecall(booking, 28)
      }
    }

    // create new SentenceCalculation and associate it with a sentence
    return SentenceCalculation(
      sentence,
      release.numberOfDaysToSentenceExpiryDate,
      release.numberOfDaysToDeterminateReleaseDateDouble,
      release.numberOfDaysToDeterminateReleaseDate,
      unadjustedExpiryDate,
      unadjustedDeterminateReleaseDate,
      numberOfDaysToPostRecallReleaseDate,
      unadjustedPostRecallReleaseDate,
      booking.calculateErsed,
      booking.adjustments,
      returnToCustodyDate = booking.returnToCustodyDate,
      numberOfDaysToParoleEligibilityDate = release.numberOfDaysToParoleEligibilityDate,
    )
  }

  private fun getSingleSentenceRelease(
    sentence: CalculableSentence,
  ): ReleaseDateCalculation {
    var numberOfDaysToParoleEligibilityDate: Long? = null
    val releaseDateMultiplier = releasePointMultiplierLookup.multiplierFor(sentence.identificationTrack)
    val custodialDuration = sentence.custodialDuration()
    val numberOfDaysToReleaseDateDouble =
      custodialDuration.getLengthInDays(sentence.sentencedAt).times(releaseDateMultiplier)
    val numberOfDaysToReleaseDate: Int = ceil(numberOfDaysToReleaseDateDouble).toInt()
    if (sentence.releaseDateTypes.getReleaseDateTypes().contains(PED) && (sentence is ExtendedDeterminateSentence || sentence is SopcSentence)) {
      val pedMultiplier = determinePedMultiplier(sentence.identificationTrack)
      numberOfDaysToParoleEligibilityDate =
        ceil(numberOfDaysToReleaseDate.toDouble().times(pedMultiplier)).toLong()
    }

    return ReleaseDateCalculation(
      sentence.getLengthInDays(),
      numberOfDaysToReleaseDateDouble,
      numberOfDaysToReleaseDate,
      numberOfDaysToParoleEligibilityDate,
    )
  }

  fun calculateFixedTermRecall(booking: Booking, days: Int): LocalDate {
    return booking.returnToCustodyDate!!
      .plusDays(days.toLong())
      .minusDays(1)
  }

  private fun determinePedMultiplier(identification: SentenceIdentificationTrack): Double {
    return when (identification) {
      SentenceIdentificationTrack.SOPC_PED_AT_TWO_THIRDS,
      SentenceIdentificationTrack.EDS_DISCRETIONARY_RELEASE,
      -> 2 / 3.toDouble()
      SentenceIdentificationTrack.SOPC_PED_AT_HALFWAY -> 1 / 2.toDouble()
      else -> throw UnsupportedOperationException("Unknown identification for a PED calculation $identification")
    }
  }

  data class ReleaseDateCalculation(
    val numberOfDaysToSentenceExpiryDate: Int,
    val numberOfDaysToDeterminateReleaseDateDouble: Double,
    val numberOfDaysToDeterminateReleaseDate: Int,
    val numberOfDaysToParoleEligibilityDate: Long?,
  )
}
