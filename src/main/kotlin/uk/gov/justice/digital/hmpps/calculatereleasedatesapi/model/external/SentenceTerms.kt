package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

data class SentenceTerms(
  val years: Int = 0,
  val months: Int = 0,
  val weeks: Int = 0,
  val days: Int = 0,
  val code: String = ""
) {
  companion object {
    const val LICENCE_TERM_CODE = "LIC"
    const val IMPRISONMENT_TERM_CODE = "IMP"
  }
}
