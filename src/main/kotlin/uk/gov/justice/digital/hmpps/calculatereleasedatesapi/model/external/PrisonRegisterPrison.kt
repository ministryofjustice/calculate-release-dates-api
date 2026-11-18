package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PrisonRegisterPrison(
  val prisonId: String,
  val prisonName: String,
  val active: Boolean,
)
