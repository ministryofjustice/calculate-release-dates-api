package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import java.time.temporal.ChronoUnit

@Service
class SentenceIdentificationService {
  fun identify(sentence: Sentence, offender: Offender) {
    if (
      sentence.sentencedAt.isBefore(ImportantDates.LASPO_DATE) &&
      sentence.offence.startedAt.isBefore(ImportantDates.CJA_DATE)
    ) {
      beforeCJAAndLASPO(sentence)
    } else {
      afterCJAAndLASPO(sentence, offender)
    }
  }

  private fun afterCJAAndLASPO(sentence: Sentence, offender: Offender) {

    sentence.identificationTrack = SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO

    if (!sentence.durationIsGreaterThanOrEqualTo(TWELVE, ChronoUnit.MONTHS)) {

      if (sentence.offence.startedAt.isAfter(ImportantDates.ORA_DATE)) {
        isTopUpSentenceExpiryDateRequired(sentence, offender)
      } else {
        sentence.sentenceTypes = listOf(
          SentenceType.ARD,
          SentenceType.SED
        )
      }
    } else {
      if (sentence.durationIsGreaterThan(TWO, ChronoUnit.YEARS)) {
        sentence.sentenceTypes = listOf(
          SentenceType.SLED,
          SentenceType.CRD
        )
      } else {

        if (sentence.offence.startedAt.isBefore(ImportantDates.ORA_DATE)) {
          sentence.sentenceTypes = listOf(
            SentenceType.SLED,
            SentenceType.CRD
          )
        } else {
          isTopUpSentenceExpiryDateRequired(sentence, offender)
        }
      }
    }
  }

  private fun beforeCJAAndLASPO(sentence: Sentence) {

    sentence.identificationTrack = SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO

    if (sentence.durationIsGreaterThanOrEqualTo(FOUR, ChronoUnit.YEARS)) {
      if (sentence.offence.isScheduleFifteen) {
        sentence.sentenceTypes = listOf(
          SentenceType.PED,
          SentenceType.NPD,
          SentenceType.LED,
          SentenceType.SED
        )
      } else {
        sentence.sentenceTypes = listOf(
          SentenceType.CRD,
          SentenceType.SLED
        )
      }
    } else if (sentence.durationIsGreaterThanOrEqualTo(TWELVE, ChronoUnit.MONTHS)) {
      sentence.sentenceTypes = listOf(
        SentenceType.LED,
        SentenceType.CRD,
        SentenceType.SED
      )
    } else {
      sentence.sentenceTypes = listOf(
        SentenceType.ARD,
        SentenceType.SED
      )
    }
  }

  private fun isTopUpSentenceExpiryDateRequired(sentence: Sentence, offender: Offender) {
    if (
      sentence.duration.getLengthInDays(sentence.sentencedAt) <= INT_ONE ||
      offender.getAgeOnDate(sentence.getHalfSentenceDate()) <= INT_EIGHTEEN
    ) {
      sentence.sentenceTypes = listOf(SentenceType.SLED, SentenceType.CRD)
    } else {
      sentence.sentenceTypes = listOf(
        SentenceType.SLED,
        SentenceType.CRD,
        SentenceType.TUSED
      )
    }
  }
  companion object {
    private const val INT_EIGHTEEN = 18
    private const val INT_ONE = 1
    private const val TWO = 2L
    private const val FOUR = 4L
    private const val TWELVE = 12L
  }
}
