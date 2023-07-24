package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Offender sentence and offence details")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SentencesOffencesTerms(
  @Schema(description = "Sentence sequence - a number representing the order")
  private val sentenceSequence: Int? = null,

  @Schema(description = "This sentence is consecutive to this sequence (if populated)")
  private val consecutiveToSequence: Int? = null,

  @Schema(description = "This sentence status: A = Active I = Inactive")
  private val sentenceStatus: String? = null,

  @Schema(description = "The sentence category e.g. 2003 or Licence")
  private val sentenceCategory: String? = null,

  @Schema(description = "The sentence calculation type e.g. R or ADIMP_ORA")
  private val sentenceCalculationType: String? = null,

  @Schema(description = "The sentence type description e.g. Standard Determinate Sentence")
  private val sentenceTypeDescription: String? = null,

  @Schema(description = "The sentence start date for this sentence (aka court date)")
  private val sentenceStartDate: LocalDate? = null,

  @Schema(description = "The sentence end date for this sentence")
  private val sentenceEndDate: LocalDate? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Fine amount.")
  private val fineAmount: Double? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sentence line number", example = "1")
  private val lineSeq: Long? = null,

  @Schema(description = "The offences related to this sentence (will usually only have one offence per sentence)")
  private val offences: List<OffenderOffence>? = null,

  @Schema(description = "The terms related to this sentence (will usually only have one term per sentence)")
  private val terms: List<Terms>? = null,
)
