package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

/**
 * A class representing questions that will need clarification from the user before we start the calculation
 */
data class CalculationUserQuestions(
  val sentenceQuestions: List<CalculationSentenceQuestion>
)
