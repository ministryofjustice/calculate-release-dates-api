package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

/**
 * A class representing the users input to questions {@see CalculationUserQuestions} we've asked before calculation.
 */
data class CalculationUserInputs(
  @Schema(description = "List of sentences and the users input for each sentence")
  val sentenceCalculationUserInputs: List<CalculationSentenceUserInput> = listOf(),
  @Schema(description = "A flag to indicate whether to calculate ERSED.")
  val calculateErsed: Boolean = false,
  @Schema(description = "Whether to use offence indicators from another system for the calculation or user's input.")
  val useOffenceIndicators: Boolean = false
)
