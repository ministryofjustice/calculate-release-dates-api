package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SupportedSentence(
  val sentenceType: String,
  val sentenceAtCondition: (LocalDate) -> Boolean = { true },
  val offenceDateCondition: (LocalDate) -> Boolean = { true },
)
