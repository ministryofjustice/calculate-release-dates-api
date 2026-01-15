package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import java.time.LocalDate

class PrisonApiDataVersions {

  class Version0 {
    data class SentenceAndOffences(
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
      val years: Int = 0,
      val months: Int = 0,
      val weeks: Int = 0,
      val days: Int = 0,
      val offences: List<OffenderOffence> = emptyList(),
    ) {
      fun toLatest() = offences.map { offence: OffenderOffence ->
        SentenceAndOffenceWithReleaseArrangements(
          bookingId = bookingId,
          sentenceSequence = sentenceSequence,
          lineSequence = lineSequence,
          caseSequence = caseSequence,
          consecutiveToSequence = consecutiveToSequence,
          sentenceStatus = sentenceStatus,
          sentenceCategory = sentenceCategory,
          sentenceCalculationType = sentenceCalculationType,
          sentenceTypeDescription = sentenceTypeDescription,
          sentenceDate = sentenceDate,
          terms = listOf(SentenceTerms(years, months, weeks, days)),
          offence = offence,
          caseReference = null,
          courtId = null,
          courtDescription = null,
          courtTypeCode = null,
          fineAmount = 0.toBigDecimal(),
          isSDSPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
        )
      }
    }
  }
}
