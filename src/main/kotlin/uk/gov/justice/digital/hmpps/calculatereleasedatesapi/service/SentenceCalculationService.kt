package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import java.time.LocalDate
import kotlin.math.ceil

@Service
class SentenceCalculationService(
  private val sentenceAdjustedCalculationService: SentenceAdjustedCalculationService,
  private val releasePointMultiplierLookup: ReleasePointMultiplierLookup,
  private val sentenceAggregator: SentenceAggregator,
) {

  fun calculate(sentence: CalculableSentence, booking: Booking, options: CalculationOptions): SentenceCalculation {
    // create association between the sentence and it's calculation
    sentence.sentenceCalculation = getSentenceCalculation(booking, sentence, options)
    return sentenceAdjustedCalculationService.calculateDatesFromAdjustments(sentence, booking)
  }

  private data class IndexedSentenceWithReleasePointMultiplier(val index: Int, val sentence: CalculableSentence, val multiplier: Double)

  private fun getConsecutiveRelease(sentence: ConsecutiveSentence, multiplerFn: (identification: SentenceIdentificationTrack) -> Double): ReleaseDateCalculation {
    val daysToExpiry = sentenceAggregator.getDaysInGroup(sentence.sentencedAt, sentence.orderedSentences) { it.totalDuration() }
    val (sentencesWithPed, sentencesWithoutPed) = sentence.orderedSentences
      .map { IndexedSentenceWithReleasePointMultiplier(sentence.orderedSentences.indexOf(it), it, multiplerFn(it.identificationTrack)) }
      .partition { (it.sentence is ExtendedDeterminateSentence && !it.sentence.automaticRelease) || it.sentence is SopcSentence }
    val sentencesWithoutPedGroupedByMultiplierAndGroupsSortedByFirstAppearance = sentencesWithoutPed
      .groupBy { it.multiplier }.entries
      .sortedBy { (_, sentences) -> sentences.minOf { it.index } }
      .map { it.value }

    val sentencesInCalculationOrder = sentencesWithoutPedGroupedByMultiplierAndGroupsSortedByFirstAppearance + listOf(sentencesWithPed)

    var notionalCrd: LocalDate? = null
    var daysToRelease = 0
    var numberOfDaysToParoleEligibilityDate: Long? = null
    sentencesInCalculationOrder.forEach { sentencesWithMultipliers ->
      if (sentencesWithMultipliers.isNotEmpty()) {
        val releaseStartDate = if (notionalCrd != null) notionalCrd!!.plusDays(1) else sentence.sentencedAt
        val daysInThisCustodialDuration = sentenceAggregator.getDaysInGroup(releaseStartDate, sentencesWithMultipliers.map { it.sentence }) { it.custodialDuration() }
        if (sentencesWithMultipliers == sentencesWithPed && !sentence.isRecall()) {
          numberOfDaysToParoleEligibilityDate = calculateConsecutivePed(sentencesWithMultipliers.map { it.sentence }, daysToRelease, releaseStartDate)
        }
        val multiplier = sentencesWithMultipliers[0].multiplier
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

    val daysInFirstCustodialDuration = sentenceAggregator.getDaysInGroup(releaseStartDate, sentencesOfFirstType) { it.custodialDuration() }
    var daysToPed = ceil(daysInFirstCustodialDuration.times(firstSentenceMultiplier)).toLong()
    val notionalPed = releaseStartDate
      .plusDays(daysToPed)
      .minusDays(1)
    if (sentencesOfOtherType.isNotEmpty()) {
      val otherSentenceMultiplier = determinePedMultiplier(sentencesOfOtherType[0].identificationTrack)
      val daysInOtherCustodialDuration = sentenceAggregator.getDaysInGroup(notionalPed, sentencesOfOtherType) { it.custodialDuration() }
      daysToPed += ceil(daysInOtherCustodialDuration.times(otherSentenceMultiplier)).toLong()
    }
    return daysToRelease + daysToPed
  }

  private fun getSentenceCalculation(booking: Booking, sentence: CalculableSentence, options: CalculationOptions): SentenceCalculation {
    val (release, historicRelease) = if (sentence is ConsecutiveSentence) {
      getConsecutiveRelease(sentence) { releasePointMultiplierLookup.multiplierFor(it) } to getConsecutiveRelease(sentence) { releasePointMultiplierLookup.historicMultiplierFor(it) }
    } else {
      getSingleSentenceRelease(sentence) { releasePointMultiplierLookup.multiplierFor(it) } to getSingleSentenceRelease(sentence) { releasePointMultiplierLookup.historicMultiplierFor(it) }
    }

    val expiryDateDaysToAdd = if (sentence.isBotusConsecutiveToSDS()) {
      booking.sentences[0].totalDuration().getLengthInDays(booking.sentences[0].sentencedAt).toLong()
    } else if (sentence.isSdsConsecutiveToBotus()) {
      booking.sentences[1].totalDuration().getLengthInDays(booking.sentences[1].sentencedAt).toLong()
    } else {
      release.numberOfDaysToSentenceExpiryDate.toLong()
    }

    val unadjustedExpiryDate = sentence.sentencedAt.plusDays(expiryDateDaysToAdd).minusDays(1)

    val unadjustedDeterminateReleaseDate =
      sentence.sentencedAt
        .plusDays(release.numberOfDaysToDeterminateReleaseDate.toLong())
        .minusDays(1)

    val unadjustedHistoricDeterminateReleaseDate =
      sentence.sentencedAt
        .plusDays(historicRelease.numberOfDaysToDeterminateReleaseDate.toLong())
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
      historicRelease.numberOfDaysToDeterminateReleaseDate,
      unadjustedHistoricDeterminateReleaseDate,
      unadjustedExpiryDate,
      unadjustedDeterminateReleaseDate,
      numberOfDaysToPostRecallReleaseDate,
      unadjustedPostRecallReleaseDate,
      options.calculateErsed,
      booking.adjustments,
      returnToCustodyDate = booking.returnToCustodyDate,
      numberOfDaysToParoleEligibilityDate = release.numberOfDaysToParoleEligibilityDate,
    )
  }

  private fun getSingleSentenceRelease(
    sentence: CalculableSentence,
    multiplerFn: (identification: SentenceIdentificationTrack) -> Double,
  ): ReleaseDateCalculation {
    var numberOfDaysToParoleEligibilityDate: Long? = null
    val releaseDateMultiplier = multiplerFn(sentence.identificationTrack)
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
