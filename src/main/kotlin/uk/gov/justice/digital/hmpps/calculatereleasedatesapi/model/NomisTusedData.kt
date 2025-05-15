package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class NomisTusedData(
  val latestTused: LocalDate?,
  var latestOverrideTused: LocalDate?,
  val comment: String?,
  val offenderNo: String,
) {
  fun getLatestTusedDate(): LocalDate? = latestOverrideTused ?: latestTused
}
