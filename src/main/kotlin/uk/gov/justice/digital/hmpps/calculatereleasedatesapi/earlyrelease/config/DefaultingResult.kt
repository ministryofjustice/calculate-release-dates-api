package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import java.time.LocalDate

data class DefaultingResult(val date: LocalDate, val outcome: DefaultingOutcome)
