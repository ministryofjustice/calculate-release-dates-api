package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SentenceDiagramRowSection(
    val start: LocalDate,
    val end: LocalDate,
    val description: String?
)