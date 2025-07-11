package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoValidReturnToCustodyDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceAggregator
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.properties.Delegates

class UnadjustedReleaseDate(
  val sentence: CalculableSentence,
  findMultiplierBySentence: (sentence: CalculableSentence) -> Double,
  historicFindMultiplierBySentence: (sentence: CalculableSentence) -> Double,
  val returnToCustodyDate: LocalDate? = null,
) {

  val historicReleaseDateCalculation: ReleaseDateCalculation

  /*
    When the multiplier is changed (At tranching for SDS40). The delegate here will re-calculate the unadjusted release dates.
   */
  var findMultiplierBySentence: (sentence: CalculableSentence) -> Double by Delegates.observable(findMultiplierBySentence) { _, _, _ ->
    calculateUnadjustedReleaseDate()
  }

  init {
    this.findMultiplierBySentence = findMultiplierBySentence
    this.historicReleaseDateCalculation = if (sentence is ConsecutiveSentence) {
      getConsecutiveRelease(historicFindMultiplierBySentence)
    } else {
      getSingleSentenceRelease(historicFindMultiplierBySentence)
    }
  }

  lateinit var releaseDateCalculation: ReleaseDateCalculation
    private set

  val unadjustedExpiryDate: LocalDate
    get() = sentence.sentencedAt
      .plusDays(releaseDateCalculation.numberOfDaysToSentenceExpiryDate.toLong())
      .minusDays(1)

  val unadjustedDeterminateReleaseDate: LocalDate
    get() = sentence.sentencedAt
      .plusDays(releaseDateCalculation.numberOfDaysToDeterminateReleaseDate.toLong())
      .minusDays(1)

  val unadjustedHistoricDeterminateReleaseDate: LocalDate
    get() {
      return sentence.sentencedAt
        .plusDays(historicReleaseDateCalculation.numberOfDaysToDeterminateReleaseDate.toLong())
        .minusDays(1)
    }

  var numberOfDaysToPostRecallReleaseDate: Int? = null
    private set

  var unadjustedPostRecallReleaseDate: LocalDate? = null
    private set

  private fun calculateFixedTermRecall(days: Int): LocalDate {
    if (returnToCustodyDate == null) {
      throw NoValidReturnToCustodyDateException("No return to custody date available")
    }
    return returnToCustodyDate
      .plusDays(days.toLong())
      .minusDays(1)
  }

  private fun calculateUnadjustedReleaseDate() {
    this.releaseDateCalculation = if (sentence is ConsecutiveSentence) {
      getConsecutiveRelease(findMultiplierBySentence)
    } else {
      getSingleSentenceRelease(findMultiplierBySentence)
    }

    if (sentence.isRecall()) {
      when (val recallType = sentence.recallType) {
        RecallType.STANDARD_RECALL -> {
          this.numberOfDaysToPostRecallReleaseDate = releaseDateCalculation.numberOfDaysToSentenceExpiryDate
          this.unadjustedPostRecallReleaseDate = unadjustedExpiryDate
        }

        RecallType.FIXED_TERM_RECALL_14,
        RecallType.FIXED_TERM_RECALL_28,
        RecallType.FIXED_TERM_RECALL_56,
        -> {
          val days = recallType.lengthInDays!!
          this.numberOfDaysToPostRecallReleaseDate = days
          this.unadjustedPostRecallReleaseDate = calculateFixedTermRecall(days)
        }

        RecallType.STANDARD_RECALL_255 ->
          error("STANDARD_RECALL_255 is not supported yet")
        null ->
          error("Recall type is missing, with a recall, on sentence: $sentence")
      }
    }
  }

  private fun getSingleSentenceRelease(findMultiplierBySentence: (sentence: CalculableSentence) -> Double): ReleaseDateCalculation {
    var numberOfDaysToParoleEligibilityDate: Long? = null
    val custodialDuration = sentence.custodialDuration()
    val numberOfDaysToReleaseDateDouble =
      custodialDuration.getLengthInDays(sentence.sentencedAt).times(findMultiplierBySentence(sentence))
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

  private fun getConsecutiveRelease(findMultiplierBySentence: (sentence: AbstractSentence) -> Double): ReleaseDateCalculation {
    sentence as ConsecutiveSentence
    val daysToExpiry = SentenceAggregator().getDaysInGroup(sentence.sentencedAt, sentence.orderedSentences) { it.totalDuration() }
    val (sentencesWithPed, sentencesWithoutPed) = sentence.orderedSentences
      .map {
        IndexedSentenceWithReleasePointMultiplier(
          sentence.orderedSentences.indexOf(it),
          it,
          findMultiplierBySentence(it),
        )
      }
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

        val daysInThisCustodialDuration = SentenceAggregator()
          .getDaysInGroup(
            releaseStartDate,
            sentencesWithMultipliers.map { it.sentence },
          ) { it.custodialDuration() }

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

    val daysInFirstCustodialDuration = SentenceAggregator().getDaysInGroup(releaseStartDate, sentencesOfFirstType) { it.custodialDuration() }
    var daysToPed = ceil(daysInFirstCustodialDuration.times(firstSentenceMultiplier)).toLong()
    val notionalPed = releaseStartDate
      .plusDays(daysToPed)
      .minusDays(1)
    if (sentencesOfOtherType.isNotEmpty()) {
      val otherSentenceMultiplier = determinePedMultiplier(sentencesOfOtherType[0].identificationTrack)
      val daysInOtherCustodialDuration = SentenceAggregator().getDaysInGroup(notionalPed, sentencesOfOtherType) { it.custodialDuration() }
      daysToPed += ceil(daysInOtherCustodialDuration.times(otherSentenceMultiplier)).toLong()
    }
    return daysToRelease + daysToPed
  }

  private fun determinePedMultiplier(identification: SentenceIdentificationTrack): Double = when (identification) {
    SentenceIdentificationTrack.SOPC_PED_AT_TWO_THIRDS,
    SentenceIdentificationTrack.EDS_DISCRETIONARY_RELEASE,
    -> 2 / 3.toDouble()

    SentenceIdentificationTrack.SOPC_PED_AT_HALFWAY -> 1 / 2.toDouble()
    else -> throw UnsupportedOperationException("Unknown identification for a PED calculation $identification")
  }
}

private data class IndexedSentenceWithReleasePointMultiplier(val index: Int, val sentence: CalculableSentence, val multiplier: Double)

data class ReleaseDateCalculation(
  val numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToDeterminateReleaseDateDouble: Double,
  val numberOfDaysToDeterminateReleaseDate: Int,
  val numberOfDaysToParoleEligibilityDate: Long?,
)
