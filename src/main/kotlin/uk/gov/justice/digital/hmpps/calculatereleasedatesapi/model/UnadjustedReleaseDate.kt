package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.RecallCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoValidReturnToCustodyDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoValidRevocationDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceAggregator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.rmi.UnexpectedException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.properties.Delegates

class UnadjustedReleaseDate(
  val sentence: CalculableSentence,
  val earlyReleaseConfigurations: EarlyReleaseConfigurations,
  calculationTrigger: CalculationTrigger,
) {

  var calculationTrigger: CalculationTrigger by Delegates.observable(calculationTrigger) { _, _, _ -> recalculate() }

  init {
    this.calculationTrigger = calculationTrigger
  }

  lateinit var releaseDateCalculation: ReleaseDateCalculation
    private set
  lateinit var historicReleaseDateCalculation: ReleaseDateCalculation
    private set
  var numberOfDaysToPostRecallReleaseDate: Int? = null
    private set
  var unadjustedPostRecallReleaseDate: LocalDate? = null
    private set

  private fun recalculate() {
    releaseDateCalculation = calculateReleaseDate(this::multiplier)
    historicReleaseDateCalculation = calculateReleaseDate(this::historicMultiplier)
    val recallCalculation = findRecallCalculation()
    numberOfDaysToPostRecallReleaseDate = recallCalculation?.first
    unadjustedPostRecallReleaseDate = recallCalculation?.second
  }

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

  private fun calculateReleaseDate(findMultiplierBySentence: (sentence: CalculableSentence) -> Double): ReleaseDateCalculation = if (sentence is ConsecutiveSentence) {
    getConsecutiveRelease(findMultiplierBySentence)
  } else {
    getSingleSentenceRelease(findMultiplierBySentence)
  }

  fun multiplier(sentence: CalculableSentence): Double = multiplierForSentence(
    calculationTrigger.timelineCalculationDate,
    calculationTrigger.allocatedTranche?.date,
    sentence,
  )

  fun historicMultiplier(sentence: CalculableSentence) = multiplierForSentence(
    earlyReleaseConfigurations.configurations.minOfOrNull { it.earliestTranche() }?.minusDays(1)
      ?: calculationTrigger.timelineCalculationDate,
    null,
    sentence,
  )

  fun findRecallCalculation(): Pair<Int, LocalDate>? {
    val standardCalculation = releaseDateCalculation.numberOfDaysToSentenceExpiryDate to unadjustedExpiryDate
    val revocationDate = sentence.recall?.revocationDate
    val returnToCustodyDate = sentence.recall?.returnToCustodyDate
    return when (val recallType = sentence.recallType) {
      RecallType.STANDARD_RECALL -> standardCalculation

      RecallType.FIXED_TERM_RECALL_14,
      RecallType.FIXED_TERM_RECALL_28,
      -> calculateFixedTermRecall(returnToCustodyDate, recallType)

      RecallType.FIXED_TERM_RECALL_56 -> {
        if (returnToCustodyDate == null) {
          throw NoValidReturnToCustodyDateException("No return to custody date available")
        }

        if (revocationDate == null) {
          throw NoValidRevocationDateException("No revocation date available")
        }

        val ftr56Configuration = earlyReleaseConfigurations.configurations.find { it.recallCalculation == RecallCalculationType.FTR_56 }
        val revocationDateOrReturnToCustodyDateAfterFtr56Commencement = ftr56Configuration != null && returnToCustodyDate.isAfterOrEqualTo(ftr56Configuration.earliestTranche())
        val allocatedToFtr56Tranche = calculationTrigger.allocatedEarlyReleaseConfiguration != null && calculationTrigger.allocatedEarlyReleaseConfiguration == ftr56Configuration
        val isUnderFourYears = sentence.durationIsLessThan(1461, ChronoUnit.DAYS) // Sentences under 4 years that were recalled before FTR_56 commencement should be treated as FTR_56 sentences which are not tranched.

        if (revocationDateOrReturnToCustodyDateAfterFtr56Commencement || allocatedToFtr56Tranche || isUnderFourYears) {
          calculateFixedTermRecall(returnToCustodyDate, recallType)
        } else {
          standardCalculation
        }
      }

      RecallType.STANDARD_RECALL_255 ->
        error("STANDARD_RECALL_255 is not supported yet")

      null -> null
    }
  }

  private fun calculateFixedTermRecall(returnToCustodyDate: LocalDate?, recallType: RecallType): Pair<Int, LocalDate> {
    if (returnToCustodyDate == null) {
      throw NoValidReturnToCustodyDateException("No return to custody date available")
    }
    val days = recallType.lengthInDays!!
    return days to returnToCustodyDate
      .plusDays(days.toLong())
      .minusDays(1)
  }

  private fun multiplierForSentence(
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
    sentence: CalculableSentence,
  ): Double = if (sentence.identificationTrack.isMultiplierFixed()) {
    sentence.identificationTrack.fixedMultiplier()
  } else {
    sdsReleaseMultiplier(sentence, timelineCalculationDate, allocatedTrancheDate)
  }

  private fun sdsReleaseMultiplier(
    sentence: CalculableSentence,
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
  ): Double {
    if (sentence is StandardDeterminateSentence) {
      val latestEarlyReleaseConfig =
        earlyReleaseConfigurations.configurations
          .filter { timelineCalculationDate.isAfterOrEqualTo(it.earliestTranche()) }
          .filter { it.releaseMultiplier != null }
          .maxByOrNull { it.earliestTranche() }
      if (latestEarlyReleaseConfig != null) {
        // They are tranched.
        if (allocatedTrancheDate != null) {
          if (latestEarlyReleaseConfig.matchesFilter(sentence)) {
            return getMultiplierForConfiguration(latestEarlyReleaseConfig, timelineCalculationDate, sentence)
          }
        } else if (sentence.sentencedAt.isAfterOrEqualTo(latestEarlyReleaseConfig.earliestTranche())) {
          if (latestEarlyReleaseConfig.matchesFilter(sentence)) {
            return getMultiplierForConfiguration(latestEarlyReleaseConfig, timelineCalculationDate, sentence)
          }
        }
      }
    }
    return defaultSDSReleaseMultiplier(sentence)
  }

  private fun defaultSDSReleaseMultiplier(sentence: CalculableSentence): Double = when (sentence.identificationTrack) {
    SentenceIdentificationTrack.SDS -> 0.5
    SentenceIdentificationTrack.SDS_PLUS -> 2.toDouble().div(3)
    else -> throw UnexpectedException("Unknown default release multipler.")
  }

  private fun getMultiplierForConfiguration(
    earlyReleaseConfig: EarlyReleaseConfiguration,
    timelineCalculationDate: LocalDate,
    sentence: StandardDeterminateSentence,
  ): Double {
    val sds40Tranche3 = earlyReleaseConfig.tranches.find { it.type == EarlyReleaseTrancheType.SDS_40_TRANCHE_3 }
    if (sds40Tranche3 != null && timelineCalculationDate.isAfterOrEqualTo(sds40Tranche3.date) && sentence.hasAnSDSEarlyReleaseExclusion.trancheThreeExclusion) {
      return defaultSDSReleaseMultiplier(sentence)
    }
    return earlyReleaseConfig.releaseMultiplier!![sentence.identificationTrack]!!.toDouble()
  }

  private fun getSingleSentenceRelease(findMultiplierBySentence: (sentence: CalculableSentence) -> Double): ReleaseDateCalculation {
    var numberOfDaysToParoleEligibilityDate: Long? = null
    val custodialDuration = sentence.custodialDuration()
    val numberOfDaysToReleaseDateDouble =
      custodialDuration.getLengthInDays(sentence.sentencedAt).times(findMultiplierBySentence(sentence))
    val numberOfDaysToReleaseDate: Int = ceil(numberOfDaysToReleaseDateDouble).toInt()
    if (sentence.releaseDateTypes.getReleaseDateTypes()
        .contains(PED) &&
      (sentence is ExtendedDeterminateSentence || sentence is SopcSentence)
    ) {
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

  private fun getConsecutiveRelease(findMultiplierBySentence: (sentence: CalculableSentence) -> Double): ReleaseDateCalculation {
    sentence as ConsecutiveSentence
    val daysToExpiry =
      SentenceAggregator().getDaysInGroup(sentence.sentencedAt, sentence.orderedSentences) { it.totalDuration() }
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
    var daysToPed = ceil(daysInFirstCustodialDuration.times(firstSentenceMultiplier)).toLong()
    val notionalPed = releaseStartDate
      .plusDays(daysToPed)
      .minusDays(1)
    if (sentencesOfOtherType.isNotEmpty()) {
      val otherSentenceMultiplier = determinePedMultiplier(sentencesOfOtherType[0].identificationTrack)
      val daysInOtherCustodialDuration =
        SentenceAggregator().getDaysInGroup(notionalPed, sentencesOfOtherType) { it.custodialDuration() }
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

private data class IndexedSentenceWithReleasePointMultiplier(
  val index: Int,
  val sentence: CalculableSentence,
  val multiplier: Double,
)

data class ReleaseDateCalculation(
  val numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToDeterminateReleaseDateDouble: Double,
  val numberOfDaysToDeterminateReleaseDate: Int,
  val numberOfDaysToParoleEligibilityDate: Long?,
)

/* The timeline & early release data that can change and trigger release multipliers to change */
data class CalculationTrigger(
  val timelineCalculationDate: LocalDate,
  val allocatedEarlyReleaseConfiguration: EarlyReleaseConfiguration? = null,
  val allocatedTranche: EarlyReleaseTrancheConfiguration? = null,
)
