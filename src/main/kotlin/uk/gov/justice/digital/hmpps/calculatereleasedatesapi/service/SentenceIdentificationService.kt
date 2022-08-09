package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.EDS_AUTOMATIC_RELEASE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.EDS_DISCRETIONARY_RELEASE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_TWO_THIRDS_RELEASE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SOPC_POST_PCSC
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.CJA_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.LASPO_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PCSC_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SDS_PLUS_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit

@Service
class SentenceIdentificationService(
  private val featureToggles: FeatureToggles
) {

  fun identify(sentence: CalculableSentence, offender: Offender) {
    if (sentence is SopcSentence || (sentence is ConsecutiveSentence && sentence.hasSopcSentence())) {
      identifySopcSentence(sentence)
    } else if (sentence is ExtendedDeterminateSentence || (sentence is ConsecutiveSentence && sentence.hasExtendedSentence())) {
      identifyExtendedDeterminate(sentence)
    } else if (sentence is StandardDeterminateSentence || sentence is SingleTermSentence || (sentence is ConsecutiveSentence && sentence.allSentencesAreStandardSentences())) {
      identifyStandardDeterminate(sentence, offender)
      if (sentence.recallType != null) {
        sentence.releaseDateTypes -= HDCED
        sentence.releaseDateTypes += PRRD
      }
    }
  }
  private fun identifySopcSentence(sentence: CalculableSentence) {
    if (sentence is ConsecutiveSentence) {
      sentence.releaseDateTypes = listOf(
        SLED,
        CRD
      )
    } else {
      sentence as SopcSentence
      if (sentence.sdopcu18 || sentence.sentencedAt.isAfterOrEqualTo(PCSC_COMMENCEMENT_DATE)) {
        sentence.identificationTrack = SOPC_POST_PCSC
        sentence.releaseDateTypes = listOf(
          SLED,
          CRD,
          PED
        )
      }
    }
  }
  private fun identifyExtendedDeterminate(sentence: CalculableSentence) {
    if (sentence is ConsecutiveSentence) {
      sentence.releaseDateTypes = listOf(
        SLED,
        CRD
      )
      if (sentence.hasDiscretionaryRelease()) {
        sentence.releaseDateTypes += PED
      }
      // TODO Do consec sentences need an identificationTrack? It doesn't really fit.
    } else {
      sentence as ExtendedDeterminateSentence
      if (sentence.automaticRelease) {
        sentence.identificationTrack = EDS_AUTOMATIC_RELEASE
        sentence.releaseDateTypes = listOf(
          SLED,
          CRD
        )
      } else {
        sentence.identificationTrack = EDS_DISCRETIONARY_RELEASE
        sentence.releaseDateTypes = listOf(
          SLED,
          CRD,
          PED
        )
      }
    }
  }

  fun identifyStandardDeterminate(sentence: CalculableSentence, offender: Offender) {
    sentence.releaseDateTypes = listOf()
    if (sentence is ConsecutiveSentence) {
      if (sentence.isMadeUpOfOnlySdsTwoThirdsReleaseSentences()) {
        sentence.identificationTrack = SDS_TWO_THIRDS_RELEASE
        sentence.releaseDateTypes = listOf(
          SLED,
          CRD
        )
      } else if (sentence.isMadeUpOfBeforeAndAfterCjaLaspoSentences()) {
        sentence.identificationTrack = SDS_AFTER_CJA_LASPO
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
        sentence.identificationTrack = SDS_AFTER_CJA_LASPO
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
      sentence.durationIsLessThan(FOUR, ChronoUnit.YEARS) && !offender.isActiveSexOffender
    ) {
      sentence.releaseDateTypes += HDCED
    }
  }

  fun identifyConcurrentSentence(sentence: CalculableSentence, offender: Offender) {
    if (
      sentence.sentencedAt.isBefore(LASPO_DATE) &&
      sentence.offence.committedAt.isBefore(CJA_DATE)
    ) {
      beforeCJAAndLASPO(sentence)
    } else {
      afterCJAAndLASPOorSDSPlus(sentence, offender)
    }
  }

  private fun afterCJAAndLASPOorSDSPlus(sentence: CalculableSentence, offender: Offender) {

    sentence.identificationTrack = SDS_AFTER_CJA_LASPO

    if (sentence.durationIsLessThan(TWELVE, ChronoUnit.MONTHS) &&
      sentence.offence.committedAt.isBefore(ImportantDates.ORA_DATE)
    ) {
      sentence.releaseDateTypes = listOf(
        ARD,
        SED
      )
    } else {

      val durationGreaterThanSevenYears = sentence.durationIsGreaterThanOrEqualTo(SEVEN, ChronoUnit.YEARS)
      val durationGreaterThanFourLessThanSevenYears = sentence.durationIsGreaterThanOrEqualTo(FOUR, ChronoUnit.YEARS) &&
        sentence.durationIsLessThan(SEVEN, ChronoUnit.YEARS)
      val overEighteen = offender.getAgeOnDate(sentence.sentencedAt) > INT_EIGHTEEN

      if (sentence is StandardDeterminateSentence && sentence.sentencedAt.isAfterOrEqualTo(SDS_PLUS_COMMENCEMENT_DATE)) {
        if (sentence.sentencedAt.isAfterOrEqualTo(PCSC_COMMENCEMENT_DATE)) {
          if (sentence.section250) {
            if (durationGreaterThanSevenYears && sentence.offence.isPcscSec250) {
              sentence.identificationTrack = SDS_TWO_THIRDS_RELEASE
            }
          } else if (overEighteen) {
            if (durationGreaterThanFourLessThanSevenYears && sentence.offence.isPcscSds) {
              sentence.identificationTrack = SDS_TWO_THIRDS_RELEASE
            } else if (durationGreaterThanSevenYears && (sentence.offence.isPcscSdsPlus || sentence.offence.isScheduleFifteenMaximumLife)) {
              sentence.identificationTrack = SDS_TWO_THIRDS_RELEASE
            }
          }
        } else {
          if (overEighteen && durationGreaterThanSevenYears && sentence.offence.isScheduleFifteenMaximumLife) {
            sentence.identificationTrack = SDS_TWO_THIRDS_RELEASE
          }
        }
      }

      sentence.releaseDateTypes = listOf(
        SLED,
        CRD
      )
    }
  }

  private fun beforeCJAAndLASPO(sentence: CalculableSentence) {

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

  private fun doesTopUpSentenceExpiryDateApply(sentence: CalculableSentence, offender: Offender): Boolean {
    val oraCondition = when (sentence) {
      is StandardDeterminateSentence -> {
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
      is StandardDeterminateSentence -> {
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
    private const val SEVEN = 7L
    private const val TWELVE = 12L
  }
}
