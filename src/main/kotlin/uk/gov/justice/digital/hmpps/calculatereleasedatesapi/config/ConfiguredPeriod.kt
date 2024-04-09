package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import java.time.temporal.ChronoUnit

data class ConfiguredPeriod(val value: Long, val unit: ChronoUnit)
