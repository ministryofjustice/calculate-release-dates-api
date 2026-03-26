package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoValidReturnToCustodyDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier.Companion.toIntReleaseDays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier.Companion.toLongReleaseDays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceAggregator
import java.math.BigDecimal
import java.rmi.UnexpectedException
import java.time.LocalDate
import kotlin.properties.Delegates

class UnadjustedReleaseDate(
  val sentence: CalculableSentence,
  calculationTrigger: CalculationTrigger,
) {

  var calculationTrigger: CalculationTrigger by Delegates.observable(calculationTrigger) { _, _, _ -> recalculate() }

  init {
    this.calculationTrigger = calculationTrigger
  }

  lateinit var releaseDateCalculation: ReleaseDateCalculation
    private set
  var numberOfDaysToPostRecallReleaseDate: Int? = null
    private set
  var unadjustedPostRecallReleaseDate: LocalDate? = null
    private set
  var isStandardRecallCalculation: Boolean? = null
    private set

  private fun recalculate() {
    releaseDateCalculation = calculateReleaseDate()
    val recallCalculation = findRecallCalculation()
    numberOfDaysToPostRecallReleaseDate = recallCalculation?.numberOfDaysToPostRecallReleaseDate
    unadjustedPostRecallReleaseDate = recallCalculation?.unadjustedPostRecallReleaseDate
    isStandardRecallCalculation = recallCalculation?.isStandardRecallCalculation
  }

  val unadjustedExpiryDate: LocalDate
    get() = sentence.sentencedAt
      .plusDays(releaseDateCalculation.numberOfDaysToSentenceExpiryDate.toLong())
      .minusDays(1)

  val unadjustedDeterminateReleaseDate: LocalDate
    get() = sentence.sentencedAt
      .plusDays(releaseDateCalculation.numberOfDaysToDeterminateReleaseDate.toLong())
      .minusDays(1)

  private fun calculateReleaseDate(): ReleaseDateCalculation = if (sentence is ConsecutiveSentence) {
    getConsecutiveRelease()
  } else {
    getSingleSentenceRelease()
  }

  fun findRecallCalculation(): RecallCalculationResult? {
    val standardCalculation = releaseDateCalculation.numberOfDaysToSentenceExpiryDate to unadjustedExpiryDate
    val returnToCustodyDate = sentence.recall?.returnToCustodyDate
    return when (val recallType = sentence.recallType) {
      RecallType.STANDARD_RECALL -> RecallCalculationResult(
        standardCalculation.first,
        standardCalculation.second,
        true,
      )

      RecallType.FIXED_TERM_RECALL_14,
      RecallType.FIXED_TERM_RECALL_28,
      -> calculateFixedTermRecall(returnToCustodyDate, recallType)

      RecallType.FIXED_TERM_RECALL_56 -> {
        if (returnToCustodyDate == null) {
          throw NoValidReturnToCustodyDateException("No return to custody date available for FTR56")
        }
        if (this.calculationTrigger.ftr56Supported) {
          calculateFixedTermRecall(returnToCustodyDate, recallType)
        } else {
          RecallCalculationResult(
            standardCalculation.first,
            standardCalculation.second,
            true,
          )
        }
      }

      RecallType.STANDARD_RECALL_255 ->
        error("STANDARD_RECALL_255 is not supported yet")

      null -> null
    }
  }

  private fun calculateFixedTermRecall(returnToCustodyDate: LocalDate?, recallType: RecallType): RecallCalculationResult {
    if (returnToCustodyDate == null) {
      throw NoValidReturnToCustodyDateException("No return to custody date available")
    }
    val days = recallType.lengthInDays!!
    return RecallCalculationResult(
      days,
      returnToCustodyDate
        .plusDays(days.toLong())
        .minusDays(1),
      false,
    )
  }

  fun multiplierForSentence(
    sentence: CalculableSentence,
  ): ReleaseMultiplier = if (sentence.identificationTrack.isMultiplierFixed()) {
    sentence.identificationTrack.fixedMultiplier()
  } else if (sentence is StandardDeterminateSentence) {
    sdsReleaseMultiplier(sentence)
  } else if (sentence is SingleTermSentence) {
    singleTermedReleaseMultiplier(sentence)
  } else {
    throw IllegalStateException("The multiplier isn't fixed and the sentence isn't SDS")
  }

  private fun sdsReleaseMultiplier(sentence: StandardDeterminateSentence): ReleaseMultiplier = requireNotNull(sentence.releaseMultiplier) { "SDS did not have it's release multiplier initialised" }

  private fun singleTermedReleaseMultiplier(sentence: SingleTermSentence): ReleaseMultiplier = when (sentence.identificationTrack) {
    SentenceIdentificationTrack.SDS -> ReleaseMultiplier.ONE_HALF
    else -> throw UnexpectedException("Unknown identification track '${sentence.identificationTrack}' for SingleTermed sentence")
  }

  private fun getSingleSentenceRelease(): ReleaseDateCalculation {
    var numberOfDaysToParoleEligibilityDate: Long? = null
    val custodialDuration = sentence.custodialDuration()
    val custodialDays = custodialDuration.getLengthInDays(sentence.sentencedAt).toLong()
    val multiplier = multiplierForSentence(sentence)
    val numberOfDaysToReleaseDateDecimal = BigDecimal.valueOf(custodialDays).times(multiplier.value)

    val numberOfDaysToReleaseDate: Int = numberOfDaysToReleaseDateDecimal.toIntReleaseDays()
    if (sentence.releaseDateTypes.getReleaseDateTypes()
        .contains(PED) &&
      (sentence is ExtendedDeterminateSentence || sentence is SopcSentence)
    ) {
      val pedMultiplier = determinePedMultiplier(sentence.identificationTrack)
      numberOfDaysToParoleEligibilityDate = pedMultiplier.applyTo(numberOfDaysToReleaseDate)
    }

    return ReleaseDateCalculation(
      sentence.getLengthInDays(),
      numberOfDaysToReleaseDateDecimal,
      numberOfDaysToReleaseDate,
      numberOfDaysToParoleEligibilityDate,
    )
  }

  private fun getConsecutiveRelease(): ReleaseDateCalculation {
    sentence as ConsecutiveSentence
    val daysToExpiry =
      SentenceAggregator().getDaysInGroup(sentence.sentencedAt, sentence.orderedSentences) { it.totalDuration() }
    val (sentencesWithPed, sentencesWithoutPed) = sentence.orderedSentences
      .map {
        IndexedSentenceWithReleasePointMultiplier(
          sentence.orderedSentences.indexOf(it),
          it,
          multiplierForSentence(it).value,
        )
      }
      .partition { (it.sentence is ExtendedDeterminateSentence && !it.sentence.automaticRelease) || it.sentence is SopcSentence }
    val sentencesWithoutPedGroupedByMultiplierAndGroupsSortedByFirstAppearance = sentencesWithoutPed
      .groupBy { it.multiplier }.entries
      .sortedBy { (_, sentences) -> sentences.minOf { it.index } }
      .map { it.value }

    val sentencesInCalculationOrder =
      sentencesWithoutPedGroupedByMultiplierAndGroupsSortedByFirstAppearance + listOf(sentencesWithPed)

    var notionalCrd: LocalDate? = null
    var daysToRelease = 0
    var numberOfDaysToParoleEligibilityDate: Long? = null
    sentencesInCalculationOrder.forEach { sentencesWithMultipliers ->
      if (sentencesWithMultipliers.isNotEmpty()) {
        val releaseStartDate = if (notionalCrd != null) notionalCrd.plusDays(1) else sentence.sentencedAt

        val daysInThisCustodialDuration = SentenceAggregator()
          .getDaysInGroup(
            releaseStartDate,
            sentencesWithMultipliers.map { it.sentence },
          ) { it.custodialDuration() }

        if (sentencesWithMultipliers == sentencesWithPed && !sentence.isRecall()) {
          numberOfDaysToParoleEligibilityDate =
            calculateConsecutivePed(sentencesWithMultipliers.map { it.sentence }, daysToRelease, releaseStartDate)
        }

        val multiplier = sentencesWithMultipliers[0].multiplier
        val daysToReleaseInThisGroup = BigDecimal.valueOf(daysInThisCustodialDuration.toLong()).times(multiplier).toLongReleaseDays()

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
      BigDecimal.valueOf(daysToRelease.toLong()),
      daysToRelease,
      numberOfDaysToParoleEligibilityDate,
    )
  }

  private fun calculateConsecutivePed(
    calculableSentences: List<CalculableSentence>,
    daysToRelease: Int,
    releaseStartDate: LocalDate,
  ): Long {
    val firstSentence = calculableSentences[0]
    val firstSentenceMultiplier = determinePedMultiplier(firstSentence.identificationTrack)
    val sentencesOfFirstType =
      calculableSentences.filter { determinePedMultiplier(it.identificationTrack) == firstSentenceMultiplier }
    val sentencesOfOtherType =
      calculableSentences.filter { determinePedMultiplier(it.identificationTrack) != firstSentenceMultiplier }

    val daysInFirstCustodialDuration =
      SentenceAggregator().getDaysInGroup(releaseStartDate, sentencesOfFirstType) { it.custodialDuration() }
    var daysToPed = firstSentenceMultiplier.applyTo(daysInFirstCustodialDuration)
    val notionalPed = releaseStartDate
      .plusDays(daysToPed)
      .minusDays(1)
    if (sentencesOfOtherType.isNotEmpty()) {
      val otherSentenceMultiplier = determinePedMultiplier(sentencesOfOtherType[0].identificationTrack)
      val daysInOtherCustodialDuration =
        SentenceAggregator().getDaysInGroup(notionalPed, sentencesOfOtherType) { it.custodialDuration() }
      daysToPed += otherSentenceMultiplier.applyTo(daysInOtherCustodialDuration)
    }
    return daysToRelease + daysToPed
  }

  private fun determinePedMultiplier(identification: SentenceIdentificationTrack): ReleaseMultiplier = when (identification) {
    SentenceIdentificationTrack.SOPC_PED_AT_TWO_THIRDS,
    SentenceIdentificationTrack.EDS_DISCRETIONARY_RELEASE,
    -> ReleaseMultiplier.TWO_THIRDS

    SentenceIdentificationTrack.SOPC_PED_AT_HALFWAY -> ReleaseMultiplier.ONE_HALF
    else -> throw UnsupportedOperationException("Unknown identification for a PED calculation $identification")
  }
}

private data class IndexedSentenceWithReleasePointMultiplier(
  val index: Int,
  val sentence: CalculableSentence,
  val multiplier: BigDecimal,
)

data class ReleaseDateCalculation(
  val numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToDeterminateReleaseDateDecimal: BigDecimal,
  val numberOfDaysToDeterminateReleaseDate: Int,
  val numberOfDaysToParoleEligibilityDate: Long?,
)

/* The timeline data that can change and trigger release multipliers to change */
data class CalculationTrigger(
  val timelineCalculationDate: LocalDate,
  val ftr56Supported: Boolean = false,
)

data class RecallCalculationResult(

  val numberOfDaysToPostRecallReleaseDate: Int,
  val unadjustedPostRecallReleaseDate: LocalDate,
  val isStandardRecallCalculation: Boolean,
)
