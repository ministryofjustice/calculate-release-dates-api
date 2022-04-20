package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_SERVED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.LICENSE_UNUSED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RELEASE_UNUSED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.AdjustmentIsAfterReleaseDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CustodialPeriodExtinguishedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithRemandException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.RemandPeriodOverlapsWithSentenceException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

@Service
class BookingTimelineService(
  val sentenceCalculationService: SentenceCalculationService,
  val extractionService: SentencesExtractionService
) {
  /*
     This method walks through the timeline of all the booking, checking for gaps between the release date (before ADAs)
     and the next sentence date of all the extractable sentences.
     If there is a gap, it must be filled by any of:
     1. Served ADAs
     2. Remand
     3. Tagged bail
     4. A license recall (TODO)
     5. Untagged bail or other forms of bail that don't get recorded in NOMIS (out of scope)
    */
  fun walkTimelineOfBooking(booking: Booking): Booking {
    log.info("Building timeline of sentences")
    val sortedSentences = booking.getAllExtractableSentences().sortedBy { it.sentencedAt }
    val firstSentence = sortedSentences[0]
    var sentenceRange = firstSentence.getRangeOfSentenceBeforeAwardedDays()
    var previousSentence = firstSentence

    // Which sentences are in the same "Group". A "Group" of sentences are sentences that are concurrent to each other,
    // and there sentenceAt dates overlap with the other release date. i.e. there is no release inbetween them
    // Whenever a release happens a new group is started.
    val sentenceGroups: MutableList<MutableList<ExtractableSentence>> = mutableListOf()
    var sentencesInGroup: MutableList<ExtractableSentence> = mutableListOf()
    var previousReleaseDateReached: LocalDate? = null

    sortedSentences.forEach {
      it.sentenceCalculation.adjustmentsBefore = Collections.max(listOf(sentenceRange.end, it.sentencedAt))
      it.sentenceCalculation.adjustmentsAfter = previousReleaseDateReached
      val itRange = it.getRangeOfSentenceBeforeAwardedDays()
      if (previousSentence.sentenceType.isRecall && !it.sentenceType.isRecall) {
        // The last sentence was a recall, this one is not. Treat this as release in-between so that adjustments are not shared.
        previousReleaseDateReached = it.sentencedAt.minusDays(1)
        shareAdjustmentsThroughSentenceGroup(sentencesInGroup, sentenceRange.end)
        // Clear the sentence group and start again.
        sentenceGroups.add(sentencesInGroup)
        sentencesInGroup = mutableListOf()
        sentencesInGroup.add(it)
        it.sentenceCalculation.adjustmentsBefore = it.sentencedAt
        it.sentenceCalculation.adjustmentsAfter = previousReleaseDateReached
        if (itRange.end.isAfter(sentenceRange.end)) {
          sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
        }
      } else if (sentenceRange.isConnected(itRange)) {
        if (itRange.end.isAfter(sentenceRange.end)) {
          sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
        }
        sentencesInGroup.add(it)
      } else {
        // There is gap here.
        // 1. Check if there have been any ADAs served
        var daysBetween = DAYS.between(sentenceRange.end, it.sentencedAt)
        val adaAvailable = it.sentenceCalculation.calculatedTotalAwardedDays
        val daysAdaServed = min(daysBetween - 1, adaAvailable.toLong())
        booking.adjustments.addAdjustment(
          ADDITIONAL_DAYS_SERVED,
          Adjustment(
            numberOfDays = daysAdaServed.toInt(),
            appliesToSentencesFrom = it.sentencedAt
          )
        )

        daysBetween = DAYS.between(sentenceRange.end.plusDays(daysAdaServed), it.sentencedAt)
        if (daysBetween <= 1) {
          // The gap has been filled by served adas.
          sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
          sentencesInGroup.add(it)
        } else {
          // There is still a gap

          // 2. A release date has occurred but there are more sentences on the booking, therefore previous deductions
          // should be wiped.
          previousReleaseDateReached = previousSentence.sentenceCalculation.releaseDate
          log.info("A release occurred in booking timeline at ${previousReleaseDateReached!!}")

          // This is the ends of the sentence group. Make sure all sentences share the adjustments in this group.
          shareAdjustmentsThroughSentenceGroup(sentencesInGroup, sentenceRange.end)
          // Clear the sentence group and start again.
          sentenceGroups.add(sentencesInGroup)
          sentencesInGroup = mutableListOf()
          sentencesInGroup.add(it)
          it.sentenceCalculation.adjustmentsBefore = it.sentencedAt
          it.sentenceCalculation.adjustmentsAfter = previousReleaseDateReached
          sentenceRange = LocalDateRange.of(sentenceRange.start, itRange.end)
        }
      }
      previousSentence = it
    }
    shareAdjustmentsThroughSentenceGroup(sentencesInGroup, sentenceRange.end)

    sentenceGroups.add(sentencesInGroup)
    sentenceGroups.forEach { validateSentenceHasNotBeenExtinguished(it) }
    validateRemandPeriodsOverlapping(booking)
    validateAdditionAdjustmentsInsideLatestReleaseDate(booking)

    val expiryDate = extractionService.mostRecent(sortedSentences, SentenceCalculation::expiryDate)

    capDatesByExpiry(expiryDate, sentenceGroups)

    return booking
  }

  private fun validateAdditionAdjustmentsInsideLatestReleaseDate(booking: Booking) {
    val sentences = booking.getAllExtractableSentences()
    val latestReleaseDatePreAddedDays = sentences.maxOf { it.sentenceCalculation.releaseDateWithoutAdditions }

    val adas = booking.adjustments.getOrEmptyList(ADDITIONAL_DAYS_AWARDED)
    val radas = booking.adjustments.getOrEmptyList(RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
    val uals = booking.adjustments.getOrEmptyList(UNLAWFULLY_AT_LARGE)
    val adjustments = adas + radas + uals

    val adjustmentsAfterRelease = adjustments.filter {
      it.appliesToSentencesFrom.isAfter(latestReleaseDatePreAddedDays)
    }
    if (adjustmentsAfterRelease.isNotEmpty()) {
      var anyAda = false
      var anyRada = false
      var anyUal = false
      adjustmentsAfterRelease.forEach {
        anyAda = anyAda || adas.contains(it)
        anyRada = anyRada || radas.contains(it)
        anyUal = anyUal || uals.contains(it)
      }
      val arguments = mutableListOf<String>()
      if (anyAda)
        arguments.add(ADDITIONAL_DAYS_AWARDED.name)
      if (anyRada)
        arguments.add(RESTORATION_OF_ADDITIONAL_DAYS_AWARDED.name)
      if (anyUal)
        arguments.add(UNLAWFULLY_AT_LARGE.name)
      throw AdjustmentIsAfterReleaseDateException(
        "Adjustments are applied after latest release date of booking",
        arguments
      )
    }
  }

  private fun capDatesByExpiry(
    expiry: LocalDate,
    sentenceGroups: MutableList<MutableList<ExtractableSentence>>
  ) {
    val adjustments = sentenceGroups[0][0].sentenceCalculation.adjustments
    sentenceGroups.forEach { group ->
      val unusedDays = group.filter { it.sentenceCalculation.releaseDate.isAfter(expiry) }.maxOfOrNull { DAYS.between(expiry, it.sentenceCalculation.releaseDate) }
      if (unusedDays != null && unusedDays > 0) {
        adjustments.addAdjustment(RELEASE_UNUSED_ADA, Adjustment(group.minOf { it.sentencedAt }, unusedDays.toInt()))
        group.forEach {
          readjustDates(it)
        }
      }
      val unusedLicenseDays = group.filter {
        val ledBreakdown = it.sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.LED]
        ledBreakdown != null && ledBreakdown.rules.contains(CalculationRule.LED_CONSEC_ORA_AND_NON_ORA) && it.sentenceCalculation.licenceExpiryDate!!.isAfter(expiry)
      }.maxOfOrNull {
        DAYS.between(expiry, it.sentenceCalculation.licenceExpiryDate!!)
      }

      if (unusedLicenseDays != null && unusedLicenseDays > 0) {
        adjustments.addAdjustment(LICENSE_UNUSED_ADA, Adjustment(group.minOf { it.sentencedAt }, unusedLicenseDays.toInt()))
        group.forEach {
          readjustDates(it)
        }
      }
    }
  }

  private fun shareAdjustmentsThroughSentenceGroup(sentencesInGroup: List<ExtractableSentence>, endOfGroup: LocalDate) {
    sentencesInGroup.forEach {
      it.sentenceCalculation.adjustmentsBefore = endOfGroup
      readjustDates(it)
    }
  }

  private fun validateRemandPeriodsOverlapping(booking: Booking) {
    val remandPeriods = booking.adjustments.getOrEmptyList(REMAND)
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }
      val sentenceRanges = booking.getAllExtractableSentences().map { LocalDateRange.of(it.sentencedAt, it.sentenceCalculation.adjustedDeterminateReleaseDate) }

      val allRanges = (remandRanges + sentenceRanges).sortedBy { it.start }
      var totalRange: LocalDateRange? = null
      var previousRangeIsRemand: Boolean? = null
      var previousRange: LocalDateRange? = null

      allRanges.forEach {
        val isRemand = remandRanges.any { sentenceRange -> sentenceRange === it }
        if (totalRange == null && previousRangeIsRemand == null) {
          totalRange = it
        } else if (it.isConnected(totalRange) &&
          (previousRangeIsRemand!! || isRemand)
        ) {
          // Remand overlaps
          if (previousRangeIsRemand!! && isRemand) {
            throw RemandPeriodOverlapsWithRemandException("Remand of range ${previousRange!!} overlaps with remand of range $it")
          } else {
            throw RemandPeriodOverlapsWithSentenceException("${if (previousRangeIsRemand!!) "Remand" else "Sentence"} of range ${previousRange!!} overlaps with ${if (isRemand) "remand" else "sentence"} of range $it")
          }
        } else if (it.end.isAfter(totalRange!!.end)) {
          totalRange = LocalDateRange.of(totalRange!!.start, it.end)
        }
        previousRangeIsRemand = isRemand
        previousRange = it
      }
    }
  }

  private fun validateSentenceHasNotBeenExtinguished(sentences: List<ExtractableSentence>) {
    val determinateSentences = sentences.filter { it.sentenceType === SentenceType.STANDARD_DETERMINATE }
    if (determinateSentences.isNotEmpty()) {
      val earliestSentenceDate = determinateSentences.minOf { it.sentencedAt }
      val latestReleaseDateSentence = extractionService.mostRecentSentence(
        determinateSentences, SentenceCalculation::adjustedUncappedDeterminateReleaseDate
      )
      if (earliestSentenceDate.minusDays(1).isAfter(latestReleaseDateSentence.sentenceCalculation.adjustedUncappedDeterminateReleaseDate)) {
        val hasRemand = latestReleaseDateSentence.sentenceCalculation.getAdjustment(AdjustmentType.REMAND) != 0
        val hasTaggedBail = latestReleaseDateSentence.sentenceCalculation.getAdjustment(AdjustmentType.TAGGED_BAIL) != 0
        val arguments: MutableList<String> = mutableListOf()
        if (hasRemand) {
          arguments += AdjustmentType.REMAND.name
        }
        if (hasTaggedBail) {
          arguments += AdjustmentType.TAGGED_BAIL.name
        }
        throw CustodialPeriodExtinguishedException("Custodial period extinguished", arguments)
      }
    }
  }

  private fun readjustDates(it: ExtractableSentence) {
    sentenceCalculationService.calculateDatesFromAdjustments(it)
    log.info(it.buildString())
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
