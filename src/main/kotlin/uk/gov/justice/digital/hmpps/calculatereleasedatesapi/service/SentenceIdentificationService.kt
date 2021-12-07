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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit

@Service
class SentenceIdentificationService {

  fun identify(sentence: IdentifiableSentence, offender: Offender) {
    sentence.releaseDateTypes = listOf()
    if (sentence is ConsecutiveSentence) {
      if (sentence.isMadeUpOfBeforeAndAfterCjaLaspoSentences()) {
        // This consecutive sentence is made up of pre and post laspo date sentences. (Old and new style)
        val hasScheduleFifteen = sentence.orderedSentences.any() { it.offence.isScheduleFifteen }
        if (hasScheduleFifteen) {
          // PSI example 40
          sentence.releaseDateTypes = listOf(
            NCRD,
            PED,
            NPD,
            SLED
          )
        } else {
          // PSI example 39
          sentence.releaseDateTypes = listOf(
            SLED,
            CRD
          )
        }
      } else if (sentence.isMadeUpOfOnlyAfterCjaLaspoSentences()) {
        if (sentence.hasOraSentences() && sentence.hasNonOraSentences()) {
          // PSI example 25
          if (sentence.durationIsLessThan(TWELVE, ChronoUnit.MONTHS)) {
            sentence.releaseDateTypes = listOf(
              LED,
              SED,
              CRD,
            )
          } else {
            // PSI example 21
            sentence.releaseDateTypes = listOf(
              SLED,
              CRD
            )
          }
        }
      }
    }

    if (sentence.releaseDateTypes.isEmpty()) {
      identifyConcurrentSentence(sentence, offender)
    }

    if (doesTopUpSentenceExpiryDateApply(sentence, offender)) {
      sentence.releaseDateTypes += TUSED
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
      afterCJAAndLASPO(sentence)
    }
  }

  private fun afterCJAAndLASPO(sentence: IdentifiableSentence) {

    sentence.identificationTrack = SDS_AFTER_CJA_LASPO

    if (sentence.durationIsLessThan(TWELVE, ChronoUnit.MONTHS)) {
      if (sentence.offence.committedAt.isAfterOrEqualTo(ImportantDates.ORA_DATE)) {
        sentence.releaseDateTypes = listOf(
          SLED,
          CRD
        )
      } else {
        sentence.releaseDateTypes = listOf(
          ARD,
          SED
        )
      }
    } else {
      sentence.releaseDateTypes = listOf(
        SLED,
        CRD
      )
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

  private fun doesTopUpSentenceExpiryDateApply(sentence: IdentifiableSentence, offender: Offender): Boolean {
    val oraCondition = when (sentence) {
      is Sentence -> {
        sentence.isOraSentence()
      }
      is ConsecutiveSentence -> {
        sentence.hasOraSentences()
      }
      else -> {
        false
      }
    }

    val lapsoCondition = when (sentence) {
      is Sentence -> {
        sentence.identificationTrack == SDS_AFTER_CJA_LASPO
      }
      is ConsecutiveSentence -> {
        sentence.isMadeUpOfOnlyAfterCjaLaspoSentences()
      }
      else -> {
        false
      }
    }

    return oraCondition && lapsoCondition &&
      sentence.durationIsLessThanEqualTo(TWO, ChronoUnit.YEARS) &&
      sentence.getLengthInDays() > INT_ONE &&
      offender.getAgeOnDate(sentence.getHalfSentenceDate()) > INT_EIGHTEEN
  }

  companion object {
    private const val INT_EIGHTEEN = 18
    private const val INT_ONE = 1
    private const val TWO = 2L
    private const val FOUR = 4L
    private const val TWELVE = 12L
  }
}
