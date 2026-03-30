package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

interface LegislationWithTranches : Legislation {
  val trancheSelectionStrategy: TrancheSelectionStrategy
  val tranches: List<TrancheConfiguration>
}
