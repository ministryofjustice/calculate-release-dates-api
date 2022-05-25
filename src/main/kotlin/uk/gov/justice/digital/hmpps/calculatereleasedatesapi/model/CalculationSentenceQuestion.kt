package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

/**
 * A class representing questions that will need clarification for each sentence from the user before we start the calculation
 */
data class CalculationSentenceQuestion(
  val sentenceSequence: Int,
  val userInputType: UserInputType
)
