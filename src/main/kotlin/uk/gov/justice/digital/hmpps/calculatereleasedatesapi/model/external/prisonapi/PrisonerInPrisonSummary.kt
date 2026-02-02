package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

data class PrisonerInPrisonSummary(

  /* Prisoner Identifier */
  val prisonerNumber: String,

  /* List of date when prisoner was in prison */
  val prisonPeriod: List<PrisonPeriod>? = null,

)
