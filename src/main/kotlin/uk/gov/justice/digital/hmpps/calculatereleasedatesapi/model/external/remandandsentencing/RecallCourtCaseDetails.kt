package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

import java.time.LocalDate

data class RecallCourtCaseDetails(
  val courtCaseReference: String?,
  val courtCaseUuid: String?,
  val courtCode: String?,
  val sentencingAppearanceDate: LocalDate?,
  val bookingId: Long? = null,
  val sentences: List<RecalledSentence> = emptyList(),
)
