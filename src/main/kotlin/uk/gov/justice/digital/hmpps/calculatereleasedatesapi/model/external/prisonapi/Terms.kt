package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class Terms(
  @Schema(
    requiredMode = Schema.RequiredMode.REQUIRED,
    description = "Sentence term number within sentence.",
    example = "1",
  )
  val termSequence: Int? = null,

  @Schema(
    description = "Sentence number which this sentence follows if consecutive, otherwise concurrent.",
    example = "2",
  )
  val consecutiveTo: Int? = null,

  @Schema(description = "Sentence type, using reference data from table SENTENCE_CALC_TYPES.", example = "2")
  val sentenceType: String? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sentence term code.", example = "IMP")
  val sentenceTermCode: String? = null,

  @Schema(description = "Sentence type description.", example = "2")
  val sentenceTypeDescription: String? = null,

  @Schema(
    requiredMode = Schema.RequiredMode.REQUIRED,
    description = "Start date of sentence term.",
    example = "2018-12-31",
  )
  val startDate: LocalDate? = null,

  @Schema(description = "Sentence length years.")
  val years: Int? = null,

  @Schema(description = "Sentence length months.")
  val months: Int? = null,

  @Schema(description = "Sentence length weeks.")
  val weeks: Int? = null,

  @Schema(description = "Sentence length days.")
  val days: Int? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether this is a life sentence.")
  val lifeSentence: Boolean? = null,
)
