package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema

data class SentenceAdjustmentDetail(
  @Schema(description = "Number of additional days awarded", example = "12")
  private var additionalDaysAwarded: Int? = null,

  @io.swagger.v3.oas.annotations.media.Schema(description = "Number unlawfully at large days", example = "12")
  private val unlawfullyAtLarge: Int? = null,

  @Schema(description = "Number of lawfully at large days", example = "12")
  private val lawfullyAtLarge: Int? = null,

  @Schema(description = "Number of restored additional days awarded", example = "12")
  private val restoredAdditionalDaysAwarded: Int? = null,

  @Schema(description = "Number of special remission days", example = "12")
  private val specialRemission: Int? = null,

  @Schema(description = "Number of recall sentence remand days", example = "12")
  private val recallSentenceRemand: Int? = null,

  @Schema(description = "Number of recall sentence tagged bail days", example = "12")
  private val recallSentenceTaggedBail: Int? = null,

  @Schema(description = "Number of remand days", example = "12")
  private val remand: Int? = null,

  @Schema(description = "Number of tagged bail days", example = "12")
  private val taggedBail: Int? = null,

  @Schema(description = "Number of unused remand days", example = "12")
  private val unusedRemand: Int? = null,

)
