package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SDS_40_COMMENCEMENT_DATE
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class CalculationResultEnrichmentService(
  private val nonFridayReleaseService: NonFridayReleaseService,
  private val workingDayService: WorkingDayService,
  private val clock: Clock,
  private val featureToggles: FeatureToggles,
) {

  fun addDetailToCalculationDates(
    releaseDates: List<ReleaseDate>,
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>?,
    calculationBreakdown: CalculationBreakdown?,
    historicalTusedSource: HistoricalTusedSource? = null,
    sentenceDateOverrides: List<String> = listOf(),
  ): Map<ReleaseDateType, DetailedDate> {
    val releaseDatesMap = releaseDates.associateBy { it.type }
    return releaseDatesMap.mapValues { (_, releaseDate) ->
      DetailedDate(
        releaseDate.type,
        releaseDate.type.description,
        releaseDate.date,
        getHints(
          releaseDate.type,
          releaseDate.date,
          calculationBreakdown,
          releaseDatesMap,
          sentenceAndOffences,
          historicalTusedSource,
          sentenceDateOverrides,
        ),
      )
    }
  }

  private fun getHints(
    type: ReleaseDateType,
    date: LocalDate,
    calculationBreakdown: CalculationBreakdown?,
    releaseDates: Map<ReleaseDateType, ReleaseDate>,
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>?,
    historicalTusedSource: HistoricalTusedSource? = null,
    sentenceDateOverrides: List<String>,
  ): List<ReleaseDateHint> {
    val hints = mutableListOf<ReleaseDateHint?>()

    if (sentenceDateOverrides.contains(type.name)) {
      hints += ReleaseDateHint("Manually overridden")
    }

    hints += nonFridayReleaseDateOrWeekendAdjustmentHintOrNull(type, date)

    if (calculationBreakdown !== null && sentenceAndOffences !== null && showSDS40Hints(sentenceAndOffences, calculationBreakdown)) {
      hints += sds40Hint(type, calculationBreakdown)
    }

    hints += ardHints(type, date, sentenceAndOffences, releaseDates)
    hints += crdHints(type, date, sentenceAndOffences, releaseDates)
    hints += pedHints(type, date, sentenceAndOffences, releaseDates, calculationBreakdown)
    hints += hdcedHints(type, date, sentenceAndOffences, releaseDates, calculationBreakdown)
    hints += mtdHints(type, date, sentenceAndOffences, releaseDates)
    hints += ersedHints(type, releaseDates, calculationBreakdown)
    if (historicalTusedSource != null) {
      hints += tusedHints(type)
    }
    return hints.filterNotNull()
  }

  private fun showSDS40Hints(
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
    calculationBreakdown: CalculationBreakdown,
  ): Boolean {
    if (!featureToggles.sdsEarlyReleaseHints) return false

    if (!calculationBreakdown.showSds40Hints) return false

    return sentenceAndOffences.none {
      !it.isSDSPlus &&
        (SentenceCalculationType.isSDSPlusEligible(it.sentenceCalculationType) && it.sentenceDate.isAfter(SDS_40_COMMENCEMENT_DATE))
    }
  }

  private fun tusedHints(type: ReleaseDateType): List<ReleaseDateHint> = if (type == ReleaseDateType.TUSED) {
    listOf(ReleaseDateHint("TUSED from last calculation saved to NOMIS"))
  } else {
    emptyList()
  }

  private fun nonFridayReleaseDateOrWeekendAdjustmentHintOrNull(type: ReleaseDateType, date: LocalDate): ReleaseDateHint? = if (nonFridayReleaseService.getDate(ReleaseDate(date, type)).usePolicy) {
    ReleaseDateHint(
      "The Discretionary Friday/Pre-Bank Holiday Release Scheme Policy applies to this release date.",
      "https://www.gov.uk/government/publications/discretionary-fridaypre-bank-holiday-release-scheme-policy-framework",
    )
  } else {
    weekendAdjustmentHintOrNull(type, date)
  }

  private val longFormat = DateTimeFormatter.ofPattern("cccc, dd LLLL yyyy")

  private fun weekendAdjustmentHintOrNull(type: ReleaseDateType, date: LocalDate): ReleaseDateHint? {
    if (!type.allowsWeekendAdjustment || date.isBefore(LocalDate.now(clock))) {
      return null
    }
    val adjustedToWorkingDay = when (type) {
      ReleaseDateType.HDCED -> workingDayService.nextWorkingDay(date)
      else -> workingDayService.previousWorkingDay(date)
    }
    return if (adjustedToWorkingDay.date != date) {
      ReleaseDateHint("${adjustedToWorkingDay.date.format(longFormat)} when adjusted to a working day")
    } else {
      null
    }
  }

  private fun ardHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffence>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): ReleaseDateHint? = if (type == ReleaseDateType.ARD && displayDateBeforeMtd(date, sentencesAndOffences, releaseDates)) {
    ReleaseDateHint("The Detention and training order (DTO) release date is later than the Automatic Release Date (ARD)")
  } else {
    null
  }

  private fun crdHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffence>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): ReleaseDateHint? = if (type == ReleaseDateType.CRD && displayDateBeforeMtd(date, sentencesAndOffences, releaseDates)) {
    ReleaseDateHint("The Detention and training order (DTO) release date is later than the Conditional Release Date (CRD)")
  } else {
    null
  }

  private fun pedHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffence>?, releaseDates: Map<ReleaseDateType, ReleaseDate>, calculationBreakdown: CalculationBreakdown?): List<ReleaseDateHint> = if (type == ReleaseDateType.PED) {
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

  private fun hdcedHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffence>?, releaseDates: Map<ReleaseDateType, ReleaseDate>, calculationBreakdown: CalculationBreakdown?): List<ReleaseDateHint> = if (type == ReleaseDateType.HDCED) {
    val hints = mutableListOf<ReleaseDateHint>()
    if (calculationBreakdown?.breakdownByReleaseDateType?.containsKey(ReleaseDateType.HDCED) == true) {
      val hdcRules = calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.HDCED]!!.rules

      if (CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD in hdcRules) {
        hints.add(ReleaseDateHint(HDC_POLICY_ADJUSTED_SENTENCE_DATE_HINT, HDC_POLICY_URL))
      }

      when {
        CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE in hdcRules -> {
          hints += ReleaseDateHint("HDCED adjusted for the CRD of a concurrent sentence or default term")
        }
        CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_ACTUAL_RELEASE in hdcRules -> {
          hints += ReleaseDateHint("HDCED adjusted for the ARD of a concurrent sentence or default term")
        }
        CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_PRRD in hdcRules -> {
          hints += ReleaseDateHint("HDCED adjusted for the PRRD of a recall")
        }
      }
    }
    if (displayDateBeforeMtd(date, sentencesAndOffences, releaseDates)) {
      hints += ReleaseDateHint("The Detention and training order (DTO) release date is later than the Home detention curfew eligibility date (HDCED)")
    }
    hints
  } else {
    emptyList()
  }

  private fun mtdHints(type: ReleaseDateType, date: LocalDate, sentencesAndOffences: List<SentenceAndOffence>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): ReleaseDateHint? {
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

  private fun ersedHints(type: ReleaseDateType, releaseDates: Map<ReleaseDateType, ReleaseDate>, calculationBreakdown: CalculationBreakdown?): List<ReleaseDateHint> = if (type == ReleaseDateType.ERSED) {
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

  private fun displayDateBeforeMtd(date: LocalDate, sentencesAndOffences: List<SentenceAndOffence>?, releaseDates: Map<ReleaseDateType, ReleaseDate>): Boolean = hasConcurrentDtoAndCrdArdSentence(sentencesAndOffences) &&
    releaseDates.containsKey(ReleaseDateType.MTD) &&
    date < releaseDates[ReleaseDateType.MTD]!!.date

  private fun dateBeforeAnother(dateA: LocalDate?, dateB: LocalDate?): Boolean {
    if (dateA == null || dateB == null) {
      return false
    }
    return dateA < dateB
  }

  private fun hasConcurrentDtoAndCrdArdSentence(sentencesAndOffences: List<SentenceAndOffence>?): Boolean = sentencesAndOffences != null &&
    sentencesAndOffences.any { sentence -> sentence.sentenceCalculationType in dtoSentenceTypes } &&
    sentencesAndOffences.any { sentence -> sentence.sentenceCalculationType !in dtoSentenceTypes }

  private fun sds40Hint(type: ReleaseDateType, calculationBreakdown: CalculationBreakdown): ReleaseDateHint? {
    if (calculationBreakdown.breakdownByReleaseDateType.containsKey(type)) {
      val rules = calculationBreakdown.breakdownByReleaseDateType[type]!!.rules
      if (rules.contains(CalculationRule.SDS_EARLY_RELEASE_APPLIES) && type.isEarlyReleaseHintType && !rules.contains(CalculationRule.HDCED_ADJUSTED_TO_365_COMMENCEMENT)) {
        return ReleaseDateHint("40% date has been applied")
      }
      if ((
          rules.contains(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT) ||
            rules.contains(
              CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT,
            )
          ) &&
        type.isEarlyReleaseHintType
      ) {
        val trancheText = if (rules.contains(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT)) "1" else "2"
        return ReleaseDateHint("Defaulted to tranche $trancheText commencement")
      }
      if (rules.contains(CalculationRule.SDS_STANDARD_RELEASE_APPLIES) && type.isStandardReleaseHintType && !rules.contains(CalculationRule.HDCED_ADJUSTED_TO_365_COMMENCEMENT)) {
        return ReleaseDateHint("50% date has been applied")
      }
    }
    if (type == ReleaseDateType.TUSED && calculationBreakdown.breakdownByReleaseDateType.containsKey(ReleaseDateType.CRD)) {
      val crdRules = calculationBreakdown.breakdownByReleaseDateType[ReleaseDateType.CRD]!!.rules
      if (
        crdRules.contains(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT) ||
        crdRules.contains(CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT)
      ) {
        return ReleaseDateHint("Anniversary of 40% CRD - CRD has been defaulted to tranche commencement date")
      }
      if (crdRules.contains(CalculationRule.SDS_EARLY_RELEASE_APPLIES)) {
        return ReleaseDateHint("Anniversary of 40% CRD")
      }
    }
    return null
  }

  companion object {
    private val dtoSentenceTypes = listOf(SentenceCalculationType.DTO_ORA.name, SentenceCalculationType.DTO.name)
    private const val HDC_POLICY_ADJUSTED_SENTENCE_DATE_HINT = "Adjusted for sentence date, plus 14 days as per HDC policy"
    private const val HDC_POLICY_URL = "https://assets.publishing.service.gov.uk/media/66701aa6fdbf70d6d79d9705/Home_Detention_Curfew_V7___002_.pdf"
  }
}
