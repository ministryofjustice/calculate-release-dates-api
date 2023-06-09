package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class ManualCalculationResponse(val enteredDates: Map<ReleaseDateType, LocalDate?>?)
