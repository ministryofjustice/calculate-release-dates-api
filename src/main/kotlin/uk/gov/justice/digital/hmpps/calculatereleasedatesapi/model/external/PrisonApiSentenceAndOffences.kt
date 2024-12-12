package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import java.math.BigDecimal
import java.time.LocalDate

data class PrisonApiSentenceAndOffences(
  val bookingId: Long,
  val sentenceSequence: Int,
  val lineSequence: Int,
  val caseSequence: Int,
  val consecutiveToSequence: Int? = null,
  val sentenceStatus: String,
  val sentenceCategory: String,
  val sentenceCalculationType: String,
  val sentenceTypeDescription: String,
  val sentenceDate: LocalDate,
  val terms: List<SentenceTerms> = emptyList(),
  val offences: List<OffenderOffence> = emptyList(),
  val caseReference: String? = null,
  val courtDescription: String? = null,
  val fineAmount: BigDecimal? = null,
) {
  fun toLatest(): List<SentenceAndOffenceWithReleaseArrangements> = offences.map { offence ->
    SentenceAndOffenceWithReleaseArrangements(
      source = this,
      offence = offence,
      isSdsPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
    )
  }
}
