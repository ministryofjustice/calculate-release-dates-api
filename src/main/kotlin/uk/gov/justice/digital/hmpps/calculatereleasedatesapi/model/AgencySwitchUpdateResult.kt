package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class AgencySwitchUpdateResult(
  val requiredAgencies: Set<String>,
  val agenciesSwitchedOn: Set<String>,
  val agenciesSwitchedOff: Set<String>,
  val current: List<Agency>,
)
