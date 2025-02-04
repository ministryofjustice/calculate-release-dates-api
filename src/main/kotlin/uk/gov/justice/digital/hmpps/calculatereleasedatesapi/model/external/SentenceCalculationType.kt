package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.IndeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

private val UNSUPPORTED: Class<out AbstractSentence>? = null

// These SentenceCalculationType values come from NOMIS - they map to offender_sentences.sentence_calc_type in NOMIS
enum class SentenceCalculationType(
  val recallType: RecallType? = null,
  val sentenceClazz: Class<out AbstractSentence>? = StandardDeterminateSentence::class.java,
  val primaryName: String? = null,
  val isSDS40Eligible: Boolean = false,
  val toreraEligibilityType: ToreraEligibilityType = ToreraEligibilityType.NONE,
  val sdsPlusEligibilityType: SDSPlusEligibilityType = SDSPlusEligibilityType.NONE,
) {
  ADIMP(isSDS40Eligible = true, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  ADIMP_ORA(isSDS40Eligible = true, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  YOI(isSDS40Eligible = true, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  YOI_ORA(isSDS40Eligible = true, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  SEC91_03(isSDS40Eligible = true),
  SEC91_03_ORA(isSDS40Eligible = true),
  SEC250(isSDS40Eligible = true, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  SEC250_ORA(isSDS40Eligible = true, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  LR(recallType = STANDARD_RECALL, isSDS40Eligible = true),
  LR_ORA(recallType = STANDARD_RECALL, isSDS40Eligible = true),
  LR_YOI_ORA(recallType = STANDARD_RECALL, isSDS40Eligible = true),
  LR_SEC91_ORA(recallType = STANDARD_RECALL, isSDS40Eligible = true),
  LRSEC250_ORA(recallType = STANDARD_RECALL, isSDS40Eligible = true),
  FTR_14_ORA(recallType = FIXED_TERM_RECALL_14, primaryName = "14FTR_ORA", isSDS40Eligible = true),
  FTR_14_HDC_ORA(
    recallType = FIXED_TERM_RECALL_14,
    primaryName = "14FTRHDC_ORA",
    sentenceClazz = UNSUPPORTED,
    isSDS40Eligible = true,
  ),
  FTR(recallType = FIXED_TERM_RECALL_28, isSDS40Eligible = true),
  FTR_ORA(recallType = FIXED_TERM_RECALL_28, isSDS40Eligible = true),
  FTR_HDC_ORA(recallType = FIXED_TERM_RECALL_28, sentenceClazz = UNSUPPORTED, isSDS40Eligible = true),
  FTR_SCH15(recallType = FIXED_TERM_RECALL_28, isSDS40Eligible = true),
  FTRSCH15_ORA(recallType = FIXED_TERM_RECALL_28, isSDS40Eligible = true),
  FTRSCH18(recallType = FIXED_TERM_RECALL_28, isSDS40Eligible = true),
  FTRSCH18_ORA(recallType = FIXED_TERM_RECALL_28, isSDS40Eligible = true),
  FTR_HDC(sentenceClazz = UNSUPPORTED, isSDS40Eligible = true),
  HDR(sentenceClazz = UNSUPPORTED, isSDS40Eligible = true),
  HDR_ORA(sentenceClazz = UNSUPPORTED, isSDS40Eligible = true, recallType = STANDARD_RECALL),
  LASPO_AR(sentenceClazz = ExtendedDeterminateSentence::class.java),
  LASPO_DR(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDS18(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDS21(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDSU18(sentenceClazz = ExtendedDeterminateSentence::class.java),
  SDOPCU18(sentenceClazz = SopcSentence::class.java),
  SOPC18(sentenceClazz = SopcSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SOPC),
  SOPC21(sentenceClazz = SopcSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SOPC),
  SEC236A(sentenceClazz = SopcSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SOPC),
  AFINE(sentenceClazz = AFineSentence::class.java, primaryName = "A/FINE"),
  LR_EDS18(recallType = STANDARD_RECALL, sentenceClazz = ExtendedDeterminateSentence::class.java),
  LR_EDS21(recallType = STANDARD_RECALL, sentenceClazz = ExtendedDeterminateSentence::class.java),
  LR_EDSU18(recallType = STANDARD_RECALL, sentenceClazz = ExtendedDeterminateSentence::class.java),
  LR_LASPO_AR(recallType = STANDARD_RECALL, sentenceClazz = ExtendedDeterminateSentence::class.java),
  LR_LASPO_DR(recallType = STANDARD_RECALL, sentenceClazz = ExtendedDeterminateSentence::class.java),
  LR_SEC236A(recallType = STANDARD_RECALL, sentenceClazz = SopcSentence::class.java),
  LR_SOPC18(recallType = STANDARD_RECALL, sentenceClazz = SopcSentence::class.java),
  LR_SOPC21(recallType = STANDARD_RECALL, sentenceClazz = SopcSentence::class.java),
  DTO(sentenceClazz = DetentionAndTrainingOrderSentence::class.java),
  DTO_ORA(sentenceClazz = DetentionAndTrainingOrderSentence::class.java),
  IPP(sentenceClazz = IndeterminateSentence::class.java),
  LIFE(sentenceClazz = IndeterminateSentence::class.java),
  LIFE_IPP(sentenceClazz = IndeterminateSentence::class.java, primaryName = "LIFE/IPP"),
  LR_IPP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  MLP(sentenceClazz = IndeterminateSentence::class.java),
  DLP(sentenceClazz = IndeterminateSentence::class.java),
  ALP(sentenceClazz = IndeterminateSentence::class.java),
  LEGACY(sentenceClazz = IndeterminateSentence::class.java),
  LR_LIFE(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  HMPL(sentenceClazz = IndeterminateSentence::class.java),
  DFL(sentenceClazz = IndeterminateSentence::class.java),
  LR_ALP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  ALP_LASPO(sentenceClazz = IndeterminateSentence::class.java),
  LR_DLP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_MLP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  SEC94(sentenceClazz = IndeterminateSentence::class.java),
  SEC93_03(sentenceClazz = IndeterminateSentence::class.java),
  ALP_CODE18(sentenceClazz = IndeterminateSentence::class.java),
  DPP(sentenceClazz = IndeterminateSentence::class.java),
  SEC272(sentenceClazz = IndeterminateSentence::class.java),
  SEC275(sentenceClazz = IndeterminateSentence::class.java),
  ALP_CODE21(sentenceClazz = IndeterminateSentence::class.java),
  LR_DPP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_ALP_CDE18(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_ALP_CDE21(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_ALP_LASPO(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  ZMD(sentenceClazz = IndeterminateSentence::class.java),
  SEC93(sentenceClazz = IndeterminateSentence::class.java),
  TWENTY(sentenceClazz = IndeterminateSentence::class.java, primaryName = "20"),
  NP(sentenceClazz = UNSUPPORTED),
  LR_EPP(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL),
  CR(sentenceClazz = UNSUPPORTED),
  BOTUS(sentenceClazz = BotusSentence::class.java),
  AR(sentenceClazz = UNSUPPORTED),
  EPP(sentenceClazz = UNSUPPORTED),
  CUR_ORA(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL),
  A_FINE(sentenceClazz = UNSUPPORTED, primaryName = "A/FINE"),
  CUR(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL),
  CIVIL(sentenceClazz = UNSUPPORTED),
  EXT(sentenceClazz = UNSUPPORTED),
  LR_ES(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL),
  YRO(sentenceClazz = UNSUPPORTED),
  SEC91(sentenceClazz = UNSUPPORTED),
  VOO(sentenceClazz = UNSUPPORTED),
  TISCS(sentenceClazz = UNSUPPORTED),
  STS21(sentenceClazz = UNSUPPORTED),
  STS18(sentenceClazz = UNSUPPORTED),
  UNIDENTIFIED(sentenceClazz = UNSUPPORTED),
  ;

  companion object {

    fun from(sentenceCalculationType: String): SentenceCalculationType =
      entries.firstOrNull { it.primaryName == sentenceCalculationType }
        ?: entries.firstOrNull { it.name == sentenceCalculationType }
        ?: UNIDENTIFIED

    fun isSupported(sentenceCalculationType: String): Boolean =
      runCatching { from(sentenceCalculationType).sentenceClazz }
        .getOrNull()
        .let { clazz ->
          clazz != UNSUPPORTED && clazz != IndeterminateSentence::class.java
        }

    fun isIndeterminate(sentenceCalculationType: String): Boolean =
      runCatching { from(sentenceCalculationType).sentenceClazz }
        .getOrNull() == IndeterminateSentence::class.java

    fun isSDSPlusEligible(sentenceCalculationType: String, eligibilityType: SDSPlusEligibilityType = SDSPlusEligibilityType.NONE): Boolean {
      return when (eligibilityType) {
        SDSPlusEligibilityType.NONE -> from(sentenceCalculationType).sdsPlusEligibilityType != SDSPlusEligibilityType.NONE
        SDSPlusEligibilityType.SECTION250 -> from(sentenceCalculationType).sdsPlusEligibilityType == SDSPlusEligibilityType.SECTION250
        SDSPlusEligibilityType.SDS -> from(sentenceCalculationType).sdsPlusEligibilityType == SDSPlusEligibilityType.SDS
      }
    }

    fun isSDS40Eligible(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).isSDS40Eligible
      } catch (error: IllegalArgumentException) {
        false
      }

    fun isToreraEligible(sentenceCalculationType: String, eligibilityType: ToreraEligibilityType): Boolean {
      return try {
        from(sentenceCalculationType).toreraEligibilityType == eligibilityType
      } catch (error: IllegalArgumentException) {
        false
      }
    }

    fun isDTOType(sentenceCalculationType: String): Boolean =
      runCatching { from(sentenceCalculationType).sentenceClazz }
        .getOrNull() == DetentionAndTrainingOrderSentence::class.java
  }
  enum class SDSPlusEligibilityType {
    NONE,
    SECTION250,
    SDS,
  }

  enum class ToreraEligibilityType {
    NONE,
    SOPC,
    SDS,
  }
}
