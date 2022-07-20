package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SentenceDiagramRow(
    val description: String,
    val sections: List<SentenceDiagramRowSection>
)