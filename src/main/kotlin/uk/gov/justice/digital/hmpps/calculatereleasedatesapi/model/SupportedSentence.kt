package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SupportedSentence(
  val sentenceType: SentenceType,
  val sentenceAtCondition: (LocalDate) -> Boolean = { true },
  val offenceDateCondition: (LocalDate) -> Boolean = { true },
)
