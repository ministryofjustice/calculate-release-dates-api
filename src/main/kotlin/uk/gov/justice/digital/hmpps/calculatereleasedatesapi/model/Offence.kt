package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.Optional

data class Offence(val startedAt: LocalDate, val endedAt: Optional<LocalDate>)
