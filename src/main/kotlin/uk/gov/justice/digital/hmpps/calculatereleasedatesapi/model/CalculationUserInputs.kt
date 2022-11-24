package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

/**
 * A class representing the users input to questions {@see CalculationUserQuestions} we've asked before calculation.
 */
data class CalculationUserInputs(
  val sentenceCalculationUserInputs: List<CalculationSentenceUserInput>,
  val calculateErsed: Boolean = false
)
