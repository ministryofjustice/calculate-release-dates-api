package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import java.time.LocalDate

data class AllocatedTranche(
  val legislationName: LegislationName,
  val trancheName: TrancheName,
  val trancheDate: LocalDate?,
)
