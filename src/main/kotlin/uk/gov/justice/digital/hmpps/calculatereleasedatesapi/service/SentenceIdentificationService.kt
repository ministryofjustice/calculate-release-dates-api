package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.IdentifiableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.CJA_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.LASPO_DATE
import java.time.temporal.ChronoUnit

@Service
class SentenceIdentificationService {

  fun identify(sentence: IdentifiableSentence, offender: Offender) {
    if (sentence is ConsecutiveSentence) {
      val hasAfterCJA = sentence.orderedSentences.any() { it.identificationTrack === SDS_AFTER_CJA_LASPO }
      val hasBeforeCJA = sentence.orderedSentences.any() { it.identificationTrack === SDS_BEFORE_CJA_LASPO }
      if (hasAfterCJA && hasBeforeCJA) {
        //This consecutive sentence is made up of pre and post laspo date sentences. (Old and new style)
        val hasScheduleFifteen = sentence.orderedSentences.any() { it.offence.isScheduleFifteen }
        if (hasScheduleFifteen) {
          sentence.releaseDateTypes = listOf(
            NCRD,
            PED,
            NPD,
            SLED
          )
        } else {
          sentence.releaseDateTypes = listOf(
            SLED,
            CRD
          )
        }
      } else {
        identifyConcurrentSentence(sentence, offender)
      }
    } else {
      identifyConcurrentSentence(sentence, offender)
    }

    if (sentence.durationIsGreaterThanOrEqualTo(TWELVE, ChronoUnit.WEEKS) &&
      sentence.durationIsLessThan(FOUR, ChronoUnit.YEARS)
    ) {
      sentence.releaseDateTypes += HDCED
    }
  }

  fun identifyConcurrentSentence(sentence: IdentifiableSentence, offender: Offender) {
    if (
      sentence.sentencedAt.isBefore(LASPO_DATE) &&
      sentence.offence.committedAt.isBefore(CJA_DATE)
    ) {
      beforeCJAAndLASPO(sentence)
    } else {
      afterCJAAndLASPO(sentence, offender)
    }
  }

  private fun afterCJAAndLASPO(sentence: IdentifiableSentence, offender: Offender) {

    sentence.identificationTrack = SDS_AFTER_CJA_LASPO

    if (!sentence.durationIsGreaterThanOrEqualTo(TWELVE, ChronoUnit.MONTHS)) {

      if (sentence.offence.committedAt.isAfter(ImportantDates.ORA_DATE)) {
        isTopUpSentenceExpiryDateRequired(sentence, offender)
      } else {
        sentence.releaseDateTypes = listOf(
          ARD,
          SED
        )
      }
    } else {
      if (sentence.durationIsGreaterThan(TWO, ChronoUnit.YEARS)) {
        sentence.releaseDateTypes = listOf(
          SLED,
          CRD
        )
      } else {

        if (sentence.offence.committedAt.isBefore(ImportantDates.ORA_DATE)) {
          sentence.releaseDateTypes = listOf(
            SLED,
            CRD
          )
        } else {
          isTopUpSentenceExpiryDateRequired(sentence, offender)
        }
      }
    }
  }

  private fun beforeCJAAndLASPO(sentence: IdentifiableSentence) {

    sentence.identificationTrack = SDS_BEFORE_CJA_LASPO

    if (sentence.durationIsGreaterThanOrEqualTo(FOUR, ChronoUnit.YEARS)) {
      if (sentence.offence.isScheduleFifteen) {
        sentence.releaseDateTypes = listOf(
          PED,
          NPD,
          LED,
          SED
        )
      } else {
        sentence.releaseDateTypes = listOf(
          CRD,
          SLED
        )
      }
    } else if (sentence.durationIsGreaterThanOrEqualTo(TWELVE, ChronoUnit.MONTHS)) {
      sentence.releaseDateTypes = listOf(
        LED,
        CRD,
        SED
      )
    } else {
      sentence.releaseDateTypes = listOf(
        ARD,
        SED
      )
    }
  }

  private fun isTopUpSentenceExpiryDateRequired(sentence: IdentifiableSentence, offender: Offender) {
    if (
      sentence.getLengthInDays() <= INT_ONE ||
      offender.getAgeOnDate(sentence.getHalfSentenceDate()) <= INT_EIGHTEEN
    ) {
      sentence.releaseDateTypes = listOf(SLED, CRD)
    } else {
      sentence.releaseDateTypes = listOf(
        SLED,
        CRD,
        TUSED
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
