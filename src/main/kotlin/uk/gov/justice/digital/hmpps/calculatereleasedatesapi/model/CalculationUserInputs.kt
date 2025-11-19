package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

/**
 * A class representing the user's input to questions {@see CalculationUserQuestions} we've asked before calculation.
 */
data class CalculationUserInputs(
  @param:Schema(description = "List of sentences and the users input for each sentence")
  val sentenceCalculationUserInputs: List<CalculationSentenceUserInput> = listOf(),
  @param:Schema(description = "A flag to indicate whether to calculate ERSED.", defaultValue = "false")
  val calculateErsed: Boolean = false,
  @param:Schema(description = "Whether to use offence indicators from another system for the calculation or user's input.", defaultValue = "true")
  val useOffenceIndicators: Boolean = true,
  @param:Schema(description = "If true, use a SLED from a previous calculation if it is later than the calculated one. If false, any previously recorded SLED is ignored and the newly calculated one is used.", defaultValue = "false")
  val usePreviouslyRecordedSLEDIfFound: Boolean = false,
)
