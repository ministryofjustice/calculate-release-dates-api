package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

enum class UserInputType {
  @Deprecated("This is returned by the API to denote that PCSC changes haven't come into affect yet. As soon as we pass the PCSC commencement date on 28/06/22 this will no longer be in use.")
  SCHEDULE_15_ATTRACTING_LIFE,
  ORIGINAL,
  FOUR_TO_UNDER_SEVEN,
  SECTION_250,
  UPDATED
}
