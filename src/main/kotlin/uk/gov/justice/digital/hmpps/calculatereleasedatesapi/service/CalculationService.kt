package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate

@Service
class CalculationService(
  private val bookingCalculationService: BookingCalculationService,
  private val bookingExtractionService: BookingExtractionService,
  private val bookingTimelineService: BookingTimelineService,
  private val featureToggles: FeatureToggles
) {

  private val T1_COMMENCE = LocalDate.of(2024, 7, 1)
  private val T2_COMMENCE = LocalDate.of(2025, 2, 1)

  fun calculateReleaseDates(booking: Booking): Pair<Booking, CalculationResult> {
    val anInitialCalc = doACalc(booking.copy(), featureToggles.sdsEarlyRelease)
    if (hasAnySDSEarlyRelease(anInitialCalc.first) && hasAnyReleaseBeforeTrancheCommencement(anInitialCalc.second)) {
      val a50Calc = doACalc(booking.copy(), false)
      if (hasAllReleaseBeforeTrancheCommencement(anInitialCalc.second)) {
        return a50Calc
      } else {
        return munge(anInitialCalc, a50Calc)
      }
    }
    return anInitialCalc
  }

  private fun munge(anInitialCalc: Pair<Booking, CalculationResult>, a50Calc: Pair<Booking, CalculationResult>): Pair<Booking, CalculationResult> {
    val commencementDate = getCommencementDate(anInitialCalc.second)
    val dates = anInitialCalc.second.dates.toMutableMap()
    val breakdownByReleaseDateType = anInitialCalc.second.breakdownByReleaseDateType.toMutableMap()

    mungeADate(anInitialCalc, ReleaseDateType.CRD, a50Calc, commencementDate, dates, breakdownByReleaseDateType)
    mungeADate(anInitialCalc, ReleaseDateType.HDCED, a50Calc, commencementDate, dates, breakdownByReleaseDateType)
    mungeADate(anInitialCalc, ReleaseDateType.ERSED, a50Calc, commencementDate, dates, breakdownByReleaseDateType)

    return anInitialCalc.first to CalculationResult(
        dates,
        breakdownByReleaseDateType,
        anInitialCalc.second.otherDates,
        anInitialCalc.second.effectiveSentenceLength,
    )
  }

  private fun mungeADate(
    anInitialCalc: Pair<Booking, CalculationResult>,
    dateType: ReleaseDateType,
    a50Calc: Pair<Booking, CalculationResult>,
    commencementDate: LocalDate,
    dates: MutableMap<ReleaseDateType, LocalDate>,
    breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown>,
  ) {
    val fourx = anInitialCalc.second.dates[dateType]
    val fifty = a50Calc.second.dates[dateType]
    if (fourx != null && fourx < commencementDate && fifty != null && fifty >= commencementDate) {
      dates[dateType] = commencementDate
      breakdownByReleaseDateType[dateType] = ReleaseDateCalculationBreakdown(
        setOf(CalculationRule.ADJUSTED_TO_COMMENCEMENT_DATE),
      )
    } else if(fifty != null && fifty < commencementDate) {
      dates[dateType] = fifty
      breakdownByReleaseDateType[dateType] = ReleaseDateCalculationBreakdown(
        setOf(CalculationRule.ADJUSTED_TO_50_BEFORE_COMMENCEMENT_DATE),
      )
    }
  }

  private fun hasAnySDSEarlyRelease(anInitialCalc: Booking) =
    anInitialCalc.sentences.any { it.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE }

  private fun hasAnyReleaseBeforeTrancheCommencement(result: CalculationResult): Boolean {
    val commencementDate = getCommencementDate(result)
    return listOf(ReleaseDateType.CRD, ReleaseDateType.ERSED, ReleaseDateType.HDCED)
      .mapNotNull { result.dates[it] }
      .any { it < commencementDate }
  }

  private fun hasAllReleaseBeforeTrancheCommencement(result: CalculationResult): Boolean {
    val commencementDate = getCommencementDate(result)
    return listOf(ReleaseDateType.CRD, ReleaseDateType.ERSED, ReleaseDateType.HDCED)
      .mapNotNull { result.dates[it] }
      .all { it < commencementDate }
  }

  private fun getCommencementDate(result: CalculationResult): LocalDate {
    val today = LocalDate.now()
    val fourYearsFromNow = today.plusYears(4)
    val sentenceLengthIfAppliedToday = today.plus(result.effectiveSentenceLength)
    return if (fourYearsFromNow < sentenceLengthIfAppliedToday) {
      T1_COMMENCE
    } else {
      T2_COMMENCE
    }
  }

  private fun doACalc(booking: Booking, useEarlyRelease:Boolean): Pair<Booking, CalculationResult> {
    val workingBooking = calculate(booking, useEarlyRelease)
    // apply any rules to calculate the dates
    return workingBooking to bookingExtractionService.extract(workingBooking)
  }

  fun calculate(booking: Booking, useEarlyRelease:Boolean): Booking {
    var workingBooking: Booking = booking

    // identify the types of the sentences
    workingBooking =
      bookingCalculationService
        .identify(workingBooking, useEarlyRelease)

    // calculate the dates within the sentences (Generate initial sentence calculations)
    workingBooking =
      bookingCalculationService
        .calculate(workingBooking)

    workingBooking =
      bookingCalculationService
        .createConsecutiveSentences(workingBooking, useEarlyRelease)

    workingBooking =
      bookingCalculationService
        .createSingleTermSentences(workingBooking, useEarlyRelease)

    workingBooking = bookingTimelineService
      .walkTimelineOfBooking(workingBooking)

    return workingBooking
  }
}
