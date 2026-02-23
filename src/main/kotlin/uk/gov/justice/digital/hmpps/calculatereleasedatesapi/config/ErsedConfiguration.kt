package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import java.time.temporal.ChronoUnit

@ConfigurationProperties("ersed")
data class ErsedConfiguration(
  val ers50MaxPeriodUnit: ChronoUnit,
  val ers50MaxPeriodAmount: Long,
  val ers50ReleasePoint: ReleaseMultiplier,
  val ers30MaxPeriodUnit: ChronoUnit,
  val ers30MaxPeriodAmount: Long,
  val ers30ReleasePoint: ReleaseMultiplier,
)
