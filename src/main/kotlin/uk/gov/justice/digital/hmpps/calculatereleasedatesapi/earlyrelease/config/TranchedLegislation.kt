package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

interface TranchedLegislation {
  val configuration: EarlyReleaseConfiguration
  val trancheSelectionStrategy: TrancheSelectionStrategy

  fun commencementDate() = configuration.earliestTranche()
}
