package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class SentenceDiagramRow(
  val description: String,
  val sections: List<SentenceDiagramRowSection>
)
