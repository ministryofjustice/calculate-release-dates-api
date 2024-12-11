package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service

@Service
class DateValidationService {

  private val incompatibleDatePairs = listOf(
    Pair("CRD", "ARD"),
    Pair("HDCED", "PRRD"),
    Pair("HDCAD", "PRRD"),
    Pair("PED", "PRRD"),
    Pair("HDCED", "PED"),
    Pair("HDCAD", "PED"),
    Pair("HDCAD", "APD"),
    Pair("TUSED", "PED"),
    Pair("ARD", "LED"),
  )

  val requiredDatesInContext = mapOf(Pair("CRD", "SED") to "LED")

  fun getIncompatibleDatePairs(): List<Pair<String, String>> = incompatibleDatePairs

  fun validateDates(dates: List<String>): List<ValidationMessage> {
    val incompatibleDates = incompatibleDatePairs.filter { dates.contains(it.first) && dates.contains(it.second) }
    val missingRequiredDates = requiredDatesInContext.filter { (dateTypes, requiredDate) ->
      dates.contains(dateTypes.first) && dates.contains(dateTypes.second) && !dates.contains(requiredDate)
    }

    return missingRequiredDates.map { (dates, requiredDate) ->
      ValidationMessage(ValidationCode.DATES_MISSING_REQUIRED_TYPE, listOf(dates.first, dates.second, requiredDate))
    }.plus(
      incompatibleDates.map {
        ValidationMessage(
          ValidationCode.DATES_PAIRINGS_INVALID,
          listOf(it.first, it.second),
        )
      },
    )
  }
}
