package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

data class SentenceTerms(
  val years: Int = 0,
  val months: Int = 0,
  val weeks: Int = 0,
  val days: Int = 0,
  val code: String = IMPRISONMENT_TERM_CODE
) {
  companion object {
    const val LICENCE_TERM_CODE = "LIC"
    const val IMPRISONMENT_TERM_CODE = "IMP"
    const val BREACH_OF_SUPERVISION_REQUIREMENTS_TERM_CODE = "SEC104"
    const val BREACH_DUE_TO_IMPRISONABLE_OFFENCE_TERM_CODE = "SEC105"
  }
}
