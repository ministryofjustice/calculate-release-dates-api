package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.math.BigDecimal
import java.time.LocalDate

// This version of sentence and offence data moved SDS release arrangements into their own entity to represent the fact they are only relevant on SDS
// sentences and also to support multiple early release exclusions so that we may know if the sentence is eligible for SDS40, SDS40 Additional Excluded Offences
// and/or Progression Model release arrangements.
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
  override val courtId: String?,
  override val courtDescription: String?,
  override val courtTypeCode: String?,
  override val fineAmount: BigDecimal?,
  override val revocationDates: List<LocalDate> = emptyList(),
  val sdsReleaseArrangements: SDSReleaseArrangements? = null,
) : SentenceAndOffence {

  constructor(sentenceAndOffence: SentenceAndOffence, releaseArrangements: SDSReleaseArrangements?) : this(
    bookingId = sentenceAndOffence.bookingId,
    sentenceSequence = sentenceAndOffence.sentenceSequence,
    lineSequence = sentenceAndOffence.lineSequence,
    caseSequence = sentenceAndOffence.caseSequence,
    consecutiveToSequence = sentenceAndOffence.consecutiveToSequence,
    sentenceStatus = sentenceAndOffence.sentenceStatus,
    sentenceCategory = sentenceAndOffence.sentenceCategory,
    sentenceCalculationType = sentenceAndOffence.sentenceCalculationType,
    sentenceTypeDescription = sentenceAndOffence.sentenceTypeDescription,
    sentenceDate = sentenceAndOffence.sentenceDate,
    terms = sentenceAndOffence.terms,
    offence = sentenceAndOffence.offence,
    caseReference = sentenceAndOffence.caseReference,
    courtId = sentenceAndOffence.courtId,
    courtDescription = sentenceAndOffence.courtDescription,
    courtTypeCode = sentenceAndOffence.courtTypeCode,
    fineAmount = sentenceAndOffence.fineAmount,
    revocationDates = sentenceAndOffence.revocationDates,
    sdsReleaseArrangements = releaseArrangements,
  )

  constructor(
    source: SentenceAndOffence,
    isSdsPlus: Boolean,
    isSDSPlusEligibleSentenceTypeLengthAndOffence: Boolean,
    hasAnSDSExclusion: SDSEarlyReleaseExclusionType,
  ) : this(
    source,
    if (SentenceCalculationType.from(source.sentenceCalculationType).isSDS()) {
      SDSReleaseArrangements(
        isSDSPlus = isSdsPlus,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = isSDSPlusEligibleSentenceTypeLengthAndOffence,
        sdsEarlyReleaseExclusions = if (hasAnSDSExclusion == SDSEarlyReleaseExclusionType.NO) emptyList() else listOf(hasAnSDSExclusion),
        isSection250 = SentenceCalculationType.from(source.sentenceCalculationType).isSection250(),
      )
    } else {
      null
    },
  )
}
