package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.math.BigDecimal
import java.time.LocalDate

data class SentenceAndOffenceWithReleaseArrangements(
  override val bookingId: Long,
  override val sentenceSequence: Int,
  override val lineSequence: Int,
  override val caseSequence: Int,
  override val consecutiveToSequence: Int?,
  override val sentenceStatus: String,
  override val sentenceCategory: String,
  override val sentenceCalculationType: String,
  override val sentenceTypeDescription: String,
  override val sentenceDate: LocalDate,
  override val terms: List<SentenceTerms>,
  override val offence: OffenderOffence,
  override val caseReference: String?,
  override val courtDescription: String?,
  override val courtTypeCode: String?,
  override val fineAmount: BigDecimal?,
  override val revocationDates: List<LocalDate> = emptyList(),
  val isSDSPlus: Boolean,
  val isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean,
  val isSDSPlusOffenceInPeriod: Boolean,
  val hasAnSDSEarlyReleaseExclusion: SDSEarlyReleaseExclusionType,
) : SentenceAndOffence {

  constructor(
    source: SentenceAndOffence,
    isSdsPlus: Boolean,
    isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean,
    isSDSPlusOffenceInPeriod: Boolean,
    hasAnSDSExclusion: SDSEarlyReleaseExclusionType,
  ) : this(
    source.bookingId,
    source.sentenceSequence,
    source.lineSequence,
    source.caseSequence,
    source.consecutiveToSequence,
    source.sentenceStatus,
    source.sentenceCategory,
    source.sentenceCalculationType,
    source.sentenceTypeDescription,
    source.sentenceDate,
    source.terms,
    source.offence,
    source.caseReference,
    source.courtDescription,
    source.courtTypeCode,
    source.fineAmount,
    source.revocationDates,
    isSdsPlus,
    isSDSPlusEligibleSentenceTypeLengthAndOffence,
    isSDSPlusOffenceInPeriod,
    hasAnSDSExclusion,
  )

  constructor(sdsPlusCheckResult: SDSPlusCheckResult, hasAnSDSExclusion: SDSEarlyReleaseExclusionType) : this(

    sdsPlusCheckResult.sentenceAndOffence.bookingId,
    sdsPlusCheckResult.sentenceAndOffence.sentenceSequence,
    sdsPlusCheckResult.sentenceAndOffence.lineSequence,
    sdsPlusCheckResult.sentenceAndOffence.caseSequence,
    sdsPlusCheckResult.sentenceAndOffence.consecutiveToSequence,
    sdsPlusCheckResult.sentenceAndOffence.sentenceStatus,
    sdsPlusCheckResult.sentenceAndOffence.sentenceCategory,
    sdsPlusCheckResult.sentenceAndOffence.sentenceCalculationType,
    sdsPlusCheckResult.sentenceAndOffence.sentenceTypeDescription,
    sdsPlusCheckResult.sentenceAndOffence.sentenceDate,
    sdsPlusCheckResult.sentenceAndOffence.terms,
    sdsPlusCheckResult.sentenceAndOffence.offence,
    sdsPlusCheckResult.sentenceAndOffence.caseReference,
    sdsPlusCheckResult.sentenceAndOffence.courtDescription,
    sdsPlusCheckResult.sentenceAndOffence.courtTypeCode,
    sdsPlusCheckResult.sentenceAndOffence.fineAmount,
    sdsPlusCheckResult.sentenceAndOffence.revocationDates,
    sdsPlusCheckResult.isSDSPlus,
    sdsPlusCheckResult.isSDSPlusEligibleSentenceTypeLengthAndOffence,
    sdsPlusCheckResult.isSDSPlusOffenceInPeriod,
    hasAnSDSExclusion,
  )
  constructor(
    source: PrisonApiSentenceAndOffences,
    offence: OffenderOffence,
    isSdsPlus: Boolean,
    isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean,
    isSDSPlusOffenceInPeriod: Boolean,
    hasAnSDSExclusion: SDSEarlyReleaseExclusionType,
  ) : this(
    bookingId = source.bookingId,
    sentenceSequence = source.sentenceSequence,
    lineSequence = source.lineSequence,
    caseSequence = source.caseSequence,
    consecutiveToSequence = source.consecutiveToSequence,
    sentenceStatus = source.sentenceStatus,
    sentenceCategory = source.sentenceCategory,
    sentenceCalculationType = source.sentenceCalculationType,
    sentenceTypeDescription = source.sentenceTypeDescription,
    sentenceDate = source.sentenceDate,
    terms = source.terms,
    offence = offence,
    caseReference = source.caseReference,
    courtDescription = source.courtDescription,
    courtTypeCode = source.courtTypeCode,
    fineAmount = source.fineAmount,
    revocationDates = source.revocationDates,
    isSDSPlus = isSdsPlus,
    isSDSPlusEligibleSentenceTypeLengthAndOffence = isSDSPlusEligibleSentenceTypeLengthAndOffence,
    isSDSPlusOffenceInPeriod = isSDSPlusOffenceInPeriod,
    hasAnSDSEarlyReleaseExclusion = hasAnSDSExclusion,
  )
}
