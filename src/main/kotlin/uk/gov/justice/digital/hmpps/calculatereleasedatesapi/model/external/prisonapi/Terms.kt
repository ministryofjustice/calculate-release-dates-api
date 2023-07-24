package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class Terms(
  @Schema(
    requiredMode = Schema.RequiredMode.REQUIRED,
    description = "Sentence term number within sentence.",
    example = "1",
  )
  private val termSequence: Int? = null,

  @Schema(
    description = "Sentence number which this sentence follows if consecutive, otherwise concurrent.",
    example = "2",
  )
  private val consecutiveTo: Int? = null,

  @Schema(description = "Sentence type, using reference data from table SENTENCE_CALC_TYPES.", example = "2")
  private val sentenceType: String? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sentence term code.", example = "IMP")
  private val sentenceTermCode: String? = null,

  @Schema(description = "Sentence type description.", example = "2")
  private val sentenceTypeDescription: String? = null,

  @Schema(
    requiredMode = Schema.RequiredMode.REQUIRED,
    description = "Start date of sentence term.",
    example = "2018-12-31",
  )
  private val startDate: LocalDate? = null,

  @Schema(description = "Sentence length years.")
  private val years: Int? = null,

  @Schema(description = "Sentence length months.")
  private val months: Int? = null,

  @Schema(description = "Sentence length weeks.")
  private val weeks: Int? = null,

  @Schema(description = "Sentence length days.")
  private val days: Int? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether this is a life sentence.")
  private val lifeSentence: Boolean? = null,
)
