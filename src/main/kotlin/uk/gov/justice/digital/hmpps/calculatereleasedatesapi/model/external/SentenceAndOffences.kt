package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.math.BigDecimal
import java.time.LocalDate

interface SentenceAndOffences {
  val bookingId: Long
  val sentenceSequence: Int
  val lineSequence: Int
  val caseSequence: Int
  val consecutiveToSequence: Int?
  val sentenceStatus: String
  val sentenceCategory: String
  val sentenceCalculationType: String
  val sentenceTypeDescription: String
  val sentenceDate: LocalDate
  val terms: List<SentenceTerms>
  val offences: List<OffenderOffence>
  val caseReference: String?
  val courtDescription: String?
  val fineAmount: BigDecimal?
}
