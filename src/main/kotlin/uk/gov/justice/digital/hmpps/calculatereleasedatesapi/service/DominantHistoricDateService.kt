package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

@Service
class DominantHistoricDateService {

  fun calculateFromSled(
    sled: LocalDate,
    dominantHistoricDates: List<CalculationOutcome>,
  ): Map<ReleaseDateType, LocalDate> {
    val historicDatesByType = dominantHistoricDates.associateBy { it.calculationDateType }

    val historicSled = historicDatesByType[ReleaseDateType.SLED.name]?.outcomeDate
    val historicLed = historicDatesByType[ReleaseDateType.LED.name]?.outcomeDate
    val historicSed = historicDatesByType[ReleaseDateType.SED.name]?.outcomeDate

    val historicSledExists = historicSled != null
    val ledIsAfterSled = historicSledExists && historicLed != null && historicLed > historicSled
    val sedIsAfterLed = historicSledExists && historicSed != null && historicLed != null && historicSed > historicLed

    return when {
      historicSledExists && (ledIsAfterSled || sedIsAfterLed) -> calculateDominantLedAndSed(historicSled, historicSed, historicLed)
      historicSledExists -> calculateDominantSled(historicSled)
      else -> calculateDominantLedAndSed(sled, historicSed, historicLed)
    }
  }

  fun calculateDominantSled(historicSled: LocalDate): Map<ReleaseDateType, LocalDate> = mapOf(ReleaseDateType.SLED to historicSled)

  fun calculateDominantLedAndSed(
    sled: LocalDate,
    sed: LocalDate?,
    led: LocalDate?,
  ): Map<ReleaseDateType, LocalDate> = mapOf(
    ReleaseDateType.SED to latestDate(sled, sed),
    ReleaseDateType.LED to latestDate(sled, led),
  )

  fun latestDate(currentDate: LocalDate, historicDate: LocalDate?) = when {
    historicDate !== null && historicDate > currentDate -> historicDate
    else -> currentDate
  }
}
