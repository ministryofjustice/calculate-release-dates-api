package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ETD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.MTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.AFINE_ARD_AT_FULL_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.AFINE_ARD_AT_HALFWAY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.BOTUS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.DTO_AFTER_PCSC
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.DTO_BEFORE_PCSC
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.EDS_AUTOMATIC_RELEASE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.EDS_DISCRETIONARY_RELEASE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_PLUS_RELEASE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SOPC_PED_AT_HALFWAY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SOPC_PED_AT_TWO_THIRDS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DtoSingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.HdcedCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.A_FINE_TEN_MILLION_FULL_RELEASE_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.CJA_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.LASPO_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PCSC_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TusedCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.math.BigDecimal
import java.time.temporal.ChronoUnit

@Service
class SentenceIdentificationService(
  val tusedCalculator: TusedCalculator,
  val hdcedCalculator: HdcedCalculator,
) {

  fun identify(sentence: CalculableSentence, offender: Offender) {
    val releaseDateTypes = mutableListOf<ReleaseDateType>()
    when (sentence) {
      is ConsecutiveSentence -> {
        releaseDateTypes.addAll(identifyConsecutiveSentence(sentence, offender))
      }

      is SopcSentence -> {
        releaseDateTypes.addAll(identifySopcSentence(sentence))
      }

      is ExtendedDeterminateSentence -> {
        releaseDateTypes.addAll(identifyExtendedDeterminate(sentence))
      }

      is StandardDeterminateSentence, is SingleTermSentence -> {
        releaseDateTypes.addAll(identifyStandardDeterminate(sentence, offender))
      }

      is AFineSentence -> {
        releaseDateTypes.addAll(identifyAFineSentence(sentence))
      }

      is DetentionAndTrainingOrderSentence, is DtoSingleTermSentence -> {
        releaseDateTypes.addAll(identifyDtoSentence(sentence))
      }

      is BotusSentence -> {
        releaseDateTypes.addAll(identifyBotusSentence(sentence))
      }
    }

    if (sentence.recallType != null) {
      releaseDateTypes -= HDCED
      releaseDateTypes += PRRD
    }
    sentence.releaseDateTypes = ReleaseDateTypes(releaseDateTypes.toList(), sentence, offender)
  }

  private fun identifyBotusSentence(sentence: BotusSentence): List<ReleaseDateType> {
    sentence.identificationTrack = BOTUS
    var releaseDateTypes: List<ReleaseDateType> = mutableListOf(ARD, SED)
    if (sentence.latestTusedDate != null) {
      sentence.identificationTrack = SentenceIdentificationTrack.BOTUS_WITH_HISTORIC_TUSED
      releaseDateTypes += TUSED
    }
    return releaseDateTypes
  }

  private fun identifyDtoSentence(sentence: CalculableSentence): List<ReleaseDateType> {
    if (sentence is DtoSingleTermSentence) {
      if (sentence.standardSentences.all { it.identificationTrack == DTO_BEFORE_PCSC }) {
        sentence.identificationTrack = DTO_BEFORE_PCSC
      } else {
        sentence.identificationTrack = DTO_AFTER_PCSC
      }
    } else {
      if (sentence.sentencedAt.isBefore(PCSC_COMMENCEMENT_DATE)) {
        sentence.identificationTrack = DTO_BEFORE_PCSC
      } else {
        sentence.identificationTrack = DTO_AFTER_PCSC
      }
    }
    return listOf(
      SED,
      MTD,
      ETD,
      LTD,
      TUSED,
    )
  }

  private fun identifyAFineSentence(sentence: AFineSentence): List<ReleaseDateType> {
    if (sentence.fineAmount != null &&
      sentence.fineAmount >= TEN_MILLION &&
      sentence.sentencedAt.isAfterOrEqualTo(A_FINE_TEN_MILLION_FULL_RELEASE_DATE)
    ) {
      sentence.identificationTrack = AFINE_ARD_AT_FULL_TERM
    } else {
      sentence.identificationTrack = AFINE_ARD_AT_HALFWAY
    }
    return listOf(
      SED,
      ARD,
    )
  }

  private fun identifyConsecutiveSentence(
    sentence: ConsecutiveSentence,
    offender: Offender,
  ): List<ReleaseDateType> {
    val releaseDateTypes = mutableListOf<ReleaseDateType>()
    if (sentence.hasAnyEdsOrSopcSentence()) {
      if (sentence.hasSopcSentence() || sentence.hasDiscretionaryRelease()) {
        releaseDateTypes.addAll(
          listOf(
            SLED,
            CRD,
            PED,
          ),
        )
      } else {
        releaseDateTypes.addAll(
          listOf(
            SLED,
            CRD,
          ),
        )
      }
    } else {
      if (sentence.isMadeUpOfOnlySdsPlusSentences()) {
        releaseDateTypes.addAll(
          listOf(
            SLED,
            CRD,
          ),
        )
      } else if (sentence.isMadeUpOfOnlyDtos()) {
        if (sentence.orderedSentences.all { it.identificationTrack == DTO_BEFORE_PCSC }) {
          sentence.identificationTrack = DTO_BEFORE_PCSC
        } else {
          sentence.identificationTrack = DTO_AFTER_PCSC
        }
        releaseDateTypes.addAll(
          listOf(
            SED,
            MTD,
            ETD,
            LTD,
            TUSED,
          ),
        )
      } else if (sentence.isMadeUpOfBeforeAndAfterCjaLaspoSentences()) {
        // This consecutive sentence is made up of pre and post laspo date sentences. (Old and new style)
        releaseDateTypes.addAll(
          listOf(
            SLED,
            CRD,
          ),
        )
      } else if (sentence.isMadeUpOfOnlyAfterCjaLaspoSentences()) {
        if (sentence.hasOraSentences() && sentence.hasNonOraSentences()) {
          // PSI example 25
          if (sentence.durationIsLessThan(12, ChronoUnit.MONTHS)) {
            releaseDateTypes.addAll(
              listOf(
                LED,
                SED,
                CRD,
              ),
            )
          } else {
            // PSI example 21
            releaseDateTypes.addAll(
              listOf(
                SLED,
                CRD,
              ),
            )
          }
        } else {
          afterCJAAndLASPOorSDSPlus(sentence, releaseDateTypes)
        }
      } else if (sentence.isMadeUpOfOnlyBeforeCjaLaspoSentences()) {
        beforeCJAAndLASPO(sentence, releaseDateTypes)
      }

      if (tusedCalculator.doesTopUpSentenceExpiryDateApply(sentence, offender)) {
        releaseDateTypes += TUSED
      }

      if (hdcedCalculator.doesHdcedDateApply(sentence, offender)) {
        releaseDateTypes += HDCED
      }
    }
    return releaseDateTypes
  }

  private fun identifySopcSentence(sentence: SopcSentence): List<ReleaseDateType> {
    val releaseDateTypes = mutableListOf<ReleaseDateType>()
    if (sentence.isRecall()) {
      releaseDateTypes.addAll(
        listOf(
          SLED,
          PRRD,
        ),
      )
    } else {
      releaseDateTypes.addAll(
        listOf(
          SLED,
          CRD,
          PED,
        ),
      )
    }
    if (sentence.sdopcu18 || sentence.sentencedAt.isAfterOrEqualTo(PCSC_COMMENCEMENT_DATE)) {
      sentence.identificationTrack = SOPC_PED_AT_TWO_THIRDS
    } else {
      sentence.identificationTrack = SOPC_PED_AT_HALFWAY
    }
    return releaseDateTypes
  }

  private fun identifyExtendedDeterminate(sentence: ExtendedDeterminateSentence): List<ReleaseDateType> {
    val releaseDateTypes = mutableListOf<ReleaseDateType>()
    if (sentence.automaticRelease) {
      sentence.identificationTrack = EDS_AUTOMATIC_RELEASE
      releaseDateTypes.addAll(
        listOf(
          SLED,
          CRD,
        ),
      )
    } else {
      sentence.identificationTrack = EDS_DISCRETIONARY_RELEASE
      releaseDateTypes.addAll(
        listOf(
          SLED,
          CRD,
          PED,
        ),
      )
    }
    if (sentence.isRecall()) {
      releaseDateTypes.remove(CRD)
      releaseDateTypes.remove(PED)
      releaseDateTypes.add(PRRD)
    }
    return releaseDateTypes
  }

  fun identifyStandardDeterminate(
    sentence: CalculableSentence,
    offender: Offender,
  ): List<ReleaseDateType> {
    val releaseDateTypes = mutableListOf<ReleaseDateType>()

    if (
      sentence.sentencedAt.isBefore(LASPO_DATE) &&
      sentence.offence.committedAt.isBefore(CJA_DATE)
    ) {
      beforeCJAAndLASPO(sentence, releaseDateTypes)
    } else {
      afterCJAAndLASPOorSDSPlus(sentence, releaseDateTypes)
    }

    if (tusedCalculator.doesTopUpSentenceExpiryDateApply(sentence, offender)) {
      releaseDateTypes += TUSED
    }
    if (hdcedCalculator.doesHdcedDateApply(sentence, offender)) {
      releaseDateTypes += HDCED
    }
    return releaseDateTypes
  }

  private fun afterCJAAndLASPOorSDSPlus(
    sentence: CalculableSentence,
    releaseDateTypes: MutableList<ReleaseDateType>,
  ) {
    sentence.identificationTrack = when {
      sentence is StandardDeterminateSentence && sentence.isSDSPlus -> SDS_PLUS_RELEASE
      else -> SDS
    }

    val preOraLessThanTwelve = sentence.durationIsLessThan(TWELVE, ChronoUnit.MONTHS) &&
      sentence.offence.committedAt.isBefore(
        ImportantDates.ORA_DATE,
      )
    val oneDateSentence = sentence.durationIsLessThanEqualTo(ONE, ChronoUnit.DAYS)
    val sentenceDoesntHaveLicencePeriod = preOraLessThanTwelve || oneDateSentence
    if (sentenceDoesntHaveLicencePeriod) {
      releaseDateTypes.addAll(
        listOf(
          SED,
          ARD,
        ),
      )
    } else {
      releaseDateTypes.addAll(
        listOf(
          SLED,
          CRD,
        ),
      )
    }
  }

  private fun beforeCJAAndLASPO(
    sentence: CalculableSentence,
    releaseDateTypes: MutableList<ReleaseDateType>,
  ) {
    sentence.identificationTrack = SDS

    if (sentence.durationIsGreaterThanOrEqualTo(FOUR, ChronoUnit.YEARS)) {
      releaseDateTypes.addAll(
        listOf(
          CRD,
          SLED,
        ),
      )
    } else if (sentence.durationIsGreaterThanOrEqualTo(TWELVE, ChronoUnit.MONTHS)) {
      releaseDateTypes.addAll(
        listOf(
          LED,
          CRD,
          SED,
        ),
      )
    } else {
      releaseDateTypes.addAll(
        listOf(
          ARD,
          SED,
        ),
      )
    }
  }

  companion object {
    private const val FOUR = 4L
    private const val TWELVE = 12L
    private const val ONE = 1L
    private val TEN_MILLION = BigDecimal("10000000")
  }
}
