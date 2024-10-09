package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi

data class Schedule(
  val act: String,
  val code: String,
  val id: Int,
  val url: String,
  val partNumber: Long? = null,
  val paragraphNumber: String? = null,
  val paragraphTitle: String? = null,
  val lineReference: String? = null,
  val legislationText: String? = null,
)
