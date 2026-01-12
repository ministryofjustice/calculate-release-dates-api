package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

data class ComparisonMismatchSummary(
  val personId: String,
  val lastName: String?,
  val isValid: Boolean,
  val isMatch: Boolean,
  val validationMessages: List<ValidationMessage>,
  val shortReference: String,
  val misMatchType: MismatchType,
  val sdsSentencesIdentified: List<SentenceAndOffenceWithReleaseArrangements>,
  val establishment: String?,
  val fatalException: String?,
)
