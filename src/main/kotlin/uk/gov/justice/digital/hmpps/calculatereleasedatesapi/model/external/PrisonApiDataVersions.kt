package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

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
      fun toLatest() = offences.map { offence ->
        SentenceAndOffenceWithReleaseArrangements(
          bookingId,
          sentenceSequence,
          lineSequence,
          caseSequence,
          consecutiveToSequence,
          sentenceStatus,
          sentenceCategory,
          sentenceCalculationType,
          sentenceTypeDescription,
          sentenceDate,
          listOf(SentenceTerms(years, months, weeks, days)),
          offence,
          null,
          null,
          0.toBigDecimal(),
          false,
        )
      }
    }
  }
}
