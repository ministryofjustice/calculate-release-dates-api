package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class CalculationResultEnrichmentService(
  private val nonFridayReleaseService: NonFridayReleaseService,
  private val workingDayService: WorkingDayService,
  private val clock: Clock,
  private val prisonApiDataMapper: PrisonApiDataMapper,
) {
  companion object {
    private val typesAllowedWeekendAdjustment = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.ARD,
      ReleaseDateType.PRRD,
      ReleaseDateType.HDCED,
      ReleaseDateType.PED,
      ReleaseDateType.ETD,
      ReleaseDateType.MTD,
      ReleaseDateType.LTD,
    )
    private val dtoSentenceTypes = listOf(SentenceCalculationType.DTO_ORA.name, SentenceCalculationType.DTO.name)
  }

  fun addDetailToCalculationDates(calculationRequest: CalculationRequest, calculationBreakdown: CalculationBreakdown?): Map<ReleaseDateType, DetailedDate> {
    val releaseDates = calculationRequest.calculationOutcomes
      .filter { it.outcomeDate != null }
      .map { ReleaseDateType.valueOf(it.calculationDateType) to it.outcomeDate!! }
      .associateBy(
        { (type, _) -> type },
        { (type, date) -> ReleaseDate(date, type) },
      )
    return releaseDates.mapValues { (_, releaseDate) ->
      DetailedDate(
        releaseDate.type,
        releaseDate.type.fullName,
        releaseDate.date,
        getHints(releaseDate.type, releaseDate.date, calculationRequest, calculationBreakdown, releaseDates),
      )
    }
  }

  private fun getHints(type: ReleaseDateType, date: LocalDate, calculationRequest: CalculationRequest, calculationBreakdown: CalculationBreakdown?, releaseDates: Map<ReleaseDateType, ReleaseDate>): List<ReleaseDateHint> {
    val sentencesAndOffences = calculationRequest.sentenceAndOffences?.let { prisonApiDataMapper.mapSentencesAndOffences(calculationRequest) }

    val hints = mutableListOf<ReleaseDateHint?>()
    hints += nonFridayReleaseDateOrWeekendAdjustmentHintOrNull(type, date)
    hints += ardHints(type, date, sentencesAndOffences, releaseDates)
    hints += crdHints(type, date, sentencesAndOffences, releaseDates)
    hints += pedHints(type, date, sentencesAndOffences, releaseDates, calculationBreakdown)
    hints += hdcedHints(type, date, sentencesAndOffences, releaseDates, calculationBreakdown)
    hints += mtdHints(type, date, sentencesAndOffences, releaseDates)
    hints += ersedHints(type, releaseDates, calculationBreakdown)
    return hints.filterNotNull()
  }

  private fun nonFridayReleaseDateOrWeekendAdjustmentHintOrNull(type: ReleaseDateType, date: LocalDate): ReleaseDateHint? {
    return if (nonFridayReleaseService.getDate(ReleaseDate(date, type)).usePolicy) {
      ReleaseDateHint(
        "The Discretionary Friday/Pre-Bank Holiday Release Scheme Policy applies to this release date.",
        "https://www.gov.uk/government/publications/discretionary-fridaypre-bank-holiday-release-scheme-policy-framework",
      )
    } else {
      weekendAdjustmentHintOrNull(type, date)
    }
  }

  private val longFormat = DateTimeFormatter.ofPattern("cccc, dd LLLL yyyy")

  private fun weekendAdjustmentHintOrNull(type: ReleaseDateType, date: LocalDate): ReleaseDateHint? {
    if (type !in typesAllowedWeekendAdjustment || date.isBefore(LocalDate.now(clock))) {
      return null
    }
    val previousWorkingDay = workingDayService.previousWorkingDay(date)
    return if (previousWorkingDay.date != date) {
      ReleaseDateHint("${previousWorkingDay.date.format(longFormat)} when adjusted to a working day")
    } else {
      null
    }
  }

  private fun ardHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffences>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): ReleaseDateHint? {
    return if (type == ReleaseDateType.ARD && displayDateBeforeMtd(date, sentencesAndOffences, releaseDates)) {
      ReleaseDateHint("The Detention and training order (DTO) release date is later than the Automatic Release Date (ARD)")
    } else {
      null
    }
  }

  private fun crdHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffences>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): ReleaseDateHint? {
    return if (type == ReleaseDateType.CRD && displayDateBeforeMtd(date, sentencesAndOffences, releaseDates)) {
      ReleaseDateHint("The Detention and training order (DTO) release date is later than the Conditional Release Date (CRD)")
    } else {
      null
    }
  }

  private fun pedHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffences>?, releaseDates: Map<ReleaseDateType, ReleaseDate>, calculationBreakdown: CalculationBreakdown?): List<ReleaseDateHint> {
    return if (type == ReleaseDateType.PED) {
      val hints = mutableListOf<ReleaseDateHint>()
      if (calculationBreakdown?.breakdownByReleaseDateType?.containsKey(ReleaseDateType.PED) == true) {
        if (CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_CONDITIONAL_RELEASE in calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.PED]!!.rules) {
          hints += ReleaseDateHint("PED adjusted for the CRD of a concurrent sentence or default term")
        } else if (CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_ACTUAL_RELEASE in calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.PED]!!.rules) {
          hints += ReleaseDateHint("PED adjusted for the ARD of a concurrent sentence or default term")
        }
      }
      if (calculationBreakdown?.otherDates?.containsKey(ReleaseDateType.PRRD) == true && calculationBreakdown.otherDates[ReleaseDateType.PRRD]!!.isAfter(date)) {
        hints += ReleaseDateHint("The post recall release date (PRRD) of ${calculationBreakdown.otherDates[ReleaseDateType.PRRD]!!.format(longFormat)} is later than the PED")
      }
      if (displayDateBeforeMtd(date, sentencesAndOffences, releaseDates)) {
        hints += ReleaseDateHint("The Detention and training order (DTO) release date is later than the Parole Eligibility Date (PED)")
      }
      hints
    } else {
      emptyList()
    }
  }

  private fun hdcedHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffences>?, releaseDates: Map<ReleaseDateType, ReleaseDate>, calculationBreakdown: CalculationBreakdown?): List<ReleaseDateHint> {
    return if (type == ReleaseDateType.HDCED) {
      val hints = mutableListOf<ReleaseDateHint>()
      if (calculationBreakdown?.breakdownByReleaseDateType?.containsKey(ReleaseDateType.HDCED) == true) {
        if (CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE in calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.HDCED]!!.rules) {
          hints += ReleaseDateHint("HDCED adjusted for the CRD of a concurrent sentence or default term")
        } else if (CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_ACTUAL_RELEASE in calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.HDCED]!!.rules) {
          hints += ReleaseDateHint("HDCED adjusted for the ARD of a concurrent sentence or default term")
        }
      }
      if (calculationBreakdown?.otherDates?.containsKey(ReleaseDateType.PRRD) == true && calculationBreakdown.otherDates[ReleaseDateType.PRRD]!!.isAfter(date)) {
        hints += ReleaseDateHint("Release on HDC must not take place before the PRRD ${calculationBreakdown.otherDates[ReleaseDateType.PRRD]!!.format(longFormat)}")
      }
      if (displayDateBeforeMtd(date, sentencesAndOffences, releaseDates)) {
        hints += ReleaseDateHint("The Detention and training order (DTO) release date is later than the Home detention curfew eligibility date (HDCED)")
      }
      hints
    } else {
      emptyList()
    }
  }

  private fun mtdHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffences>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): ReleaseDateHint? {
    if (type == ReleaseDateType.MTD && hasConcurrentDtoAndCrdArdSentence(sentencesAndOffences)) {
      val hdcedBeforeMtd = dateBeforeAnother(releaseDates[ReleaseDateType.HDCED]?.date, releaseDates[ReleaseDateType.MTD]?.date)
      val pedBeforeMtd = dateBeforeAnother(releaseDates[ReleaseDateType.PED]?.date, releaseDates[ReleaseDateType.MTD]?.date)
      val mtdBeforeCrd = dateBeforeAnother(date, releaseDates[ReleaseDateType.CRD]?.date)
      val mtdBeforeArd = dateBeforeAnother(date, releaseDates[ReleaseDateType.ARD]?.date)
      val mtdBeforeHdced = dateBeforeAnother(date, releaseDates[ReleaseDateType.HDCED]?.date)
      val hdcedBeforeCrd = dateBeforeAnother(releaseDates[ReleaseDateType.HDCED]?.date, releaseDates[ReleaseDateType.CRD]?.date)
      val hdcedBeforeArd = dateBeforeAnother(releaseDates[ReleaseDateType.HDCED]?.date, releaseDates[ReleaseDateType.ARD]?.date)
      val mtdBeforePed = dateBeforeAnother(date, releaseDates[ReleaseDateType.PED]?.date)
      val pedBeforeCrd = dateBeforeAnother(releaseDates[ReleaseDateType.PED]?.date, releaseDates[ReleaseDateType.CRD]?.date)
      if (hdcedBeforeMtd || pedBeforeMtd) {
        if (mtdBeforeCrd) {
          return ReleaseDateHint("Release from Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Conditional release date)")
        }
        if (mtdBeforeArd) {
          return ReleaseDateHint("Release from Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Automatic release date)")
        }
      }
      if (mtdBeforeHdced && (hdcedBeforeCrd || hdcedBeforeArd)) {
        return ReleaseDateHint("Release from the Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Home Detention Curfew Eligibility Date)")
      }
      if (mtdBeforePed && pedBeforeCrd) {
        return ReleaseDateHint("Release from Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Parole Eligibility Date)")
      }
    }
    return null
  }

  private fun ersedHints(type: ReleaseDateType, releaseDates: Map<ReleaseDateType, ReleaseDate>, calculationBreakdown: CalculationBreakdown?): List<ReleaseDateHint> {
    return if (type == ReleaseDateType.ERSED) {
      val hints = mutableListOf<ReleaseDateHint>()
      if (calculationBreakdown?.breakdownByReleaseDateType?.containsKey(ReleaseDateType.ERSED) == true && CalculationRule.ERSED_ADJUSTED_TO_CONCURRENT_TERM in calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.ERSED]!!.rules) {
        hints += ReleaseDateHint("ERSED adjusted for the ARD of a concurrent default term")
      }
      if (dateBeforeAnother(releaseDates[ReleaseDateType.MTD]?.date, releaseDates[ReleaseDateType.CRD]?.date) && dateBeforeAnother(releaseDates[ReleaseDateType.ERSED]?.date, releaseDates[ReleaseDateType.MTD]?.date)) {
        hints += ReleaseDateHint("Adjusted to Mid term date (MTD) of the Detention and training order (DTO)")
      }
      hints
    } else {
      emptyList()
    }
  }

  private fun displayDateBeforeMtd(date: LocalDate, sentencesAndOffences: List<SentenceAndOffences>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): Boolean {
    return hasConcurrentDtoAndCrdArdSentence(sentencesAndOffences) &&
      releaseDates.containsKey(ReleaseDateType.MTD) &&
      date < releaseDates[ReleaseDateType.MTD]!!.date
  }

  private fun dateBeforeAnother(dateA: LocalDate?, dateB: LocalDate?): Boolean {
    if (dateA == null || dateB == null) {
      return false
    }
    return dateA < dateB
  }

  private fun hasConcurrentDtoAndCrdArdSentence(sentencesAndOffences: List<SentenceAndOffences>?): Boolean {
    return sentencesAndOffences != null &&
      sentencesAndOffences.any { sentence -> sentence.sentenceCalculationType in dtoSentenceTypes } &&
      sentencesAndOffences.any { sentence -> sentence.sentenceCalculationType !in dtoSentenceTypes }
  }
}
