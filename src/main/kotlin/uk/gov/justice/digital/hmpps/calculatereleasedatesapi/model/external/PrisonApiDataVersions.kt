package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangementsV4
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
        val sentenceType = SentenceCalculationType.from(sentenceCalculationType)
        SentenceAndOffenceWithReleaseArrangementsV4(
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
          sdsReleaseArrangements = if (sentenceType.isSDS()) {
            SDSReleaseArrangements(
              isSDSPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              sdsEarlyReleaseExclusions = emptyList(),
              isSection250 = sentenceType.isSection250(),
            )
          } else {
            null
          },
        )
      }
    }
  }
}
