package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

data class CourtCase(
  val courtCaseReference: String,
  val courtCaseUuid: String,
  val courtCode: String,
  val sentencingAppearanceDate: String,
  val bookingId: Int,
  val sentences: List<Sentence>,
)
