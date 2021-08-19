package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SentenceTerm(
  val bookingId: Long,
  val caseId: String? = null,
  val consecutiveTo: Int? = null,
  val days: Long = 0L,
  val fineAmount: Int? = null,
  val lifeSentence: Boolean = false,
  val lineSeq: Int? = null,
  val months: Long = 0L,
  val sentenceSequence: Int,
  val sentenceStartDate: LocalDate,
  val sentenceTermCode: String? = null,
  val sentenceType: String? = null,
  val sentenceTypeDescription: String? = null,
  val startDate: LocalDate,
  val termSequence: Int? = null,
  val weeks: Long = 0L,
  val years: Long = 0L,
)
