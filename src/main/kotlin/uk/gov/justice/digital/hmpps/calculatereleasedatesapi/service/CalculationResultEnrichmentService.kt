package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class CalculationResultEnrichmentService(
  private val nonFridayReleaseService: NonFridayReleaseService,
  private val workingDayService: WorkingDayService,
  private val clock: Clock,
  private val objectMapper: ObjectMapper,
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
    private val dtoSentenceTypes = listOf("DTO_ORA", "DTO")
  }

  fun addDetailToCalculationResults(calculationRequest: CalculationRequest, calculationBreakdown: CalculationBreakdown?): DetailedCalculationResults {
    val releaseDates = calculationRequest.calculationOutcomes
      .filter { it.outcomeDate != null }
      .map { ReleaseDateType.valueOf(it.calculationDateType) to it.outcomeDate!! }
      .associateBy(
        { (type, _) -> type },
        { (type, date) -> ReleaseDate(date, type) },
      )
    return DetailedCalculationResults(
      calculationRequest.id,
      releaseDates.mapValues { (_, releaseDate) ->
        DetailedReleaseDate(
          releaseDate.type,
          releaseDate.type.fullName,
          releaseDate.date,
          getHints(releaseDate.type, releaseDate.date, calculationRequest, calculationBreakdown, releaseDates),
        )
      },
    )
  }

  private fun getHints(type: ReleaseDateType, date: LocalDate, calculationRequest: CalculationRequest, calculationBreakdown: CalculationBreakdown?, releaseDates: Map<ReleaseDateType, ReleaseDate>): List<ReleaseDateHint> {
    val sentencesAndOffences = calculationRequest.sentenceAndOffences?.map { element -> objectMapper.treeToValue(element, SentenceAndOffences::class.java) }

    val hints = mutableListOf<ReleaseDateHint?>()
    hints += nonFridayReleaseDateOrWeekendAdjustmentHintOrNull(type, date)
    hints += ardHints(type, date, sentencesAndOffences, releaseDates)
    hints += crdHints(type, date, sentencesAndOffences, releaseDates)
    hints += pedHints(type, date, sentencesAndOffences, releaseDates, calculationBreakdown)
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
      if (displayDateBeforeMtd(date, sentencesAndOffences, releaseDates)) {
        hints += ReleaseDateHint("The Detention and training order (DTO) release date is later than the Parole Eligibility Date (PED)")
      }
      if(calculationBreakdown?.breakdownByReleaseDateType?.containsKey(ReleaseDateType.PED) == true) {
        if(CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_CONDITIONAL_RELEASE in calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.PED]!!.rules) {
          hints += ReleaseDateHint("PED adjusted for the CRD of a concurrent sentence or default term")
        }else if(CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_ACTUAL_RELEASE in calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.PED]!!.rules){
          hints += ReleaseDateHint("PED adjusted for the ARD of a concurrent sentence or default term")
        }
      }
      if(calculationBreakdown?.otherDates?.containsKey(ReleaseDateType.PRRD) == true && calculationBreakdown.otherDates[ReleaseDateType.PRRD]!!.isAfter(date)) {
        hints += ReleaseDateHint("The post recall release date (PRRD) of ${calculationBreakdown.otherDates[ReleaseDateType.PRRD]!!.format(longFormat)} is later than the PED")
      }
      hints
    } else {
      emptyList()
    }
  }

  private fun displayDateBeforeMtd(date: LocalDate, sentencesAndOffences: List<SentenceAndOffences>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): Boolean {
    return hasConcurrentDtoAndCrdArdSentence(sentencesAndOffences) &&
      releaseDates.containsKey(ReleaseDateType.MTD) &&
      dateBeforeAnother(date, releaseDates[ReleaseDateType.MTD]!!.date)
  }

  private fun dateBeforeAnother(dateA: LocalDate, dateB: LocalDate): Boolean = dateA < dateB
  private fun hasConcurrentDtoAndCrdArdSentence(sentencesAndOffences: List<SentenceAndOffences>?): Boolean {
    return sentencesAndOffences != null &&
      sentencesAndOffences.any { sentence -> sentence.sentenceCalculationType in dtoSentenceTypes } &&
      sentencesAndOffences.any { sentence -> sentence.sentenceCalculationType !in dtoSentenceTypes }
  }
}
