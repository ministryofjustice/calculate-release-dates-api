package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class SentenceAdjustments(
  val additionalDaysAwarded: Int,
  val lawfullyAtLarge: Int,
  val recallSentenceRemand: Int,
  val recallSentenceTaggedBail: Int,
  val remand: Int,
  val restoredAdditionalDaysAwarded: Int,
  val specialRemission: Int,
  val taggedBail: Int,
  val unlawfullyAtLarge: Int,
  val unusedRemand: Int,
)
