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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL_255
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

private val UNSUPPORTED: Class<out AbstractSentence>? = null

// These SentenceCalculationType values come from NOMIS - they map to offender_sentences.sentence_calc_type in NOMIS
enum class SentenceCalculationType(
  val sentenceClazz: Class<out AbstractSentence>?,
  val recallType: RecallType? = null,
  val primaryName: String? = null,
  val toreraEligibilityType: ToreraEligibilityType = ToreraEligibilityType.NONE,
  val sdsPlusEligibilityType: SDSPlusEligibilityType = SDSPlusEligibilityType.NONE,
) {
  //region SDS / ORA Sentences
  ADIMP(sentenceClazz = StandardDeterminateSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  ADIMP_ORA(sentenceClazz = StandardDeterminateSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  YOI(sentenceClazz = StandardDeterminateSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  YOI_ORA(sentenceClazz = StandardDeterminateSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  SEC250(sentenceClazz = StandardDeterminateSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  SEC250_ORA(sentenceClazz = StandardDeterminateSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  SEC91_03(sentenceClazz = StandardDeterminateSentence::class.java),
  SEC91_03_ORA(sentenceClazz = StandardDeterminateSentence::class.java),
  //endregion

  //region Extended Determinate Sentences
  LASPO_AR(sentenceClazz = ExtendedDeterminateSentence::class.java),
  LASPO_DR(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDS18(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDS21(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDSU18(sentenceClazz = ExtendedDeterminateSentence::class.java),
  //endregion

  //region SOPC Sentences
  SDOPCU18(sentenceClazz = SopcSentence::class.java),
  SOPC18(sentenceClazz = SopcSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SOPC),
  SOPC21(sentenceClazz = SopcSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SOPC),
  SEC236A(sentenceClazz = SopcSentence::class.java, toreraEligibilityType = ToreraEligibilityType.SOPC),
  //endregion

  //region Fine, BOTUS and DTO Sentences
  AFINE(sentenceClazz = AFineSentence::class.java, primaryName = "A/FINE"),
  DTO(sentenceClazz = DetentionAndTrainingOrderSentence::class.java),
  DTO_ORA(sentenceClazz = DetentionAndTrainingOrderSentence::class.java),
  BOTUS(sentenceClazz = BotusSentence::class.java),
  //endregion

  //region Standard Recall Sentences
  LR(sentenceClazz = StandardDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_ORA(sentenceClazz = StandardDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_YOI_ORA(sentenceClazz = StandardDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_SEC91_ORA(sentenceClazz = StandardDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LRSEC250_ORA(sentenceClazz = StandardDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_EDS18(sentenceClazz = ExtendedDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_EDS21(sentenceClazz = ExtendedDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_EDSU18(sentenceClazz = ExtendedDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_LASPO_AR(sentenceClazz = ExtendedDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_LASPO_DR(sentenceClazz = ExtendedDeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_SEC236A(sentenceClazz = SopcSentence::class.java, recallType = STANDARD_RECALL),
  LR_SOPC18(sentenceClazz = SopcSentence::class.java, recallType = STANDARD_RECALL),
  LR_SOPC21(sentenceClazz = SopcSentence::class.java, recallType = STANDARD_RECALL),
  //endregion

  //region Fixed Term Recall Sentences
  FTR_14_ORA(sentenceClazz = StandardDeterminateSentence::class.java, recallType = FIXED_TERM_RECALL_14, primaryName = "14FTR_ORA"),
  FTR(sentenceClazz = StandardDeterminateSentence::class.java, recallType = FIXED_TERM_RECALL_28),
  FTR_ORA(sentenceClazz = StandardDeterminateSentence::class.java, recallType = FIXED_TERM_RECALL_28),
  FTR_SCH15(sentenceClazz = StandardDeterminateSentence::class.java, recallType = FIXED_TERM_RECALL_28),
  FTRSCH15_ORA(sentenceClazz = StandardDeterminateSentence::class.java, recallType = FIXED_TERM_RECALL_28),
  FTRSCH18(sentenceClazz = StandardDeterminateSentence::class.java, recallType = FIXED_TERM_RECALL_28),
  FTRSCH18_ORA(sentenceClazz = StandardDeterminateSentence::class.java, recallType = FIXED_TERM_RECALL_28),
  //endregion

  //region Indeterminate Sentences
  IPP(sentenceClazz = IndeterminateSentence::class.java),
  LIFE(sentenceClazz = IndeterminateSentence::class.java),
  LIFE_IPP(sentenceClazz = IndeterminateSentence::class.java, primaryName = "LIFE/IPP"),
  MLP(sentenceClazz = IndeterminateSentence::class.java),
  DLP(sentenceClazz = IndeterminateSentence::class.java),
  ALP(sentenceClazz = IndeterminateSentence::class.java),
  LEGACY(sentenceClazz = IndeterminateSentence::class.java),
  HMPL(sentenceClazz = IndeterminateSentence::class.java),
  DFL(sentenceClazz = IndeterminateSentence::class.java),
  ALP_LASPO(sentenceClazz = IndeterminateSentence::class.java),
  SEC94(sentenceClazz = IndeterminateSentence::class.java),
  SEC93_03(sentenceClazz = IndeterminateSentence::class.java),
  ALP_CODE18(sentenceClazz = IndeterminateSentence::class.java),
  DPP(sentenceClazz = IndeterminateSentence::class.java),
  SEC272(sentenceClazz = IndeterminateSentence::class.java),
  SEC275(sentenceClazz = IndeterminateSentence::class.java),
  ALP_CODE21(sentenceClazz = IndeterminateSentence::class.java),
  ZMD(sentenceClazz = IndeterminateSentence::class.java),
  SEC93(sentenceClazz = IndeterminateSentence::class.java),
  TWENTY(sentenceClazz = IndeterminateSentence::class.java, primaryName = "20"),
  //endregion

  //region Indeterminate Recall Sentences
  LR_IPP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_LIFE(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_ALP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_DLP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_MLP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_DPP(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_ALP_CDE18(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_ALP_CDE21(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  LR_ALP_LASPO(sentenceClazz = IndeterminateSentence::class.java, recallType = STANDARD_RECALL),
  //endregion

  //region Unsupported Sentence Types
  NP(sentenceClazz = UNSUPPORTED),
  CR(sentenceClazz = UNSUPPORTED),
  AR(sentenceClazz = UNSUPPORTED),
  EPP(sentenceClazz = UNSUPPORTED),
  A_FINE(sentenceClazz = UNSUPPORTED, primaryName = "A/FINE"),
  CIVIL(sentenceClazz = UNSUPPORTED),
  EXT(sentenceClazz = UNSUPPORTED),
  YRO(sentenceClazz = UNSUPPORTED),
  SEC91(sentenceClazz = UNSUPPORTED),
  VOO(sentenceClazz = UNSUPPORTED),
  TISCS(sentenceClazz = UNSUPPORTED),
  STS21(sentenceClazz = UNSUPPORTED),
  STS18(sentenceClazz = UNSUPPORTED),
  //endregion

  //region Unsupported Recall Sentences
  FTR_HDC(sentenceClazz = UNSUPPORTED, recallType = FIXED_TERM_RECALL_14),
  LR_ES(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL),
  LR_EPP(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL),
  FTR_HDC_ORA(recallType = FIXED_TERM_RECALL_28, sentenceClazz = UNSUPPORTED),
  FTR_14_HDC_ORA(
    sentenceClazz = UNSUPPORTED,
    recallType = FIXED_TERM_RECALL_14,
    primaryName = "14FTRHDC_ORA",
  ),
  HDR_ORA(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL_255),
  HDR(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL_255),
  CUR(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL_255),
  CUR_ORA(sentenceClazz = UNSUPPORTED, recallType = STANDARD_RECALL_255),
  //endregion

  //region Unidentified
  UNIDENTIFIED(sentenceClazz = UNSUPPORTED),
  //endregion
  ;

  companion object {

    fun from(sentenceCalculationType: String): SentenceCalculationType =
      entries.firstOrNull { it.primaryName == sentenceCalculationType }
        ?: entries.firstOrNull { it.name == sentenceCalculationType }
        ?: UNIDENTIFIED

    fun isSupported(sentenceCalculationType: String): Boolean =
      !(isIndeterminate(sentenceCalculationType) || isUnsupported(sentenceCalculationType))

    fun isIndeterminate(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).sentenceClazz == IndeterminateSentence::class.java
      } catch (error: IllegalArgumentException) {
        false
      }

    private fun isUnsupported(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).sentenceClazz == null
      } catch (error: IllegalArgumentException) {
        false
      }

    fun isSDSPlusEligible(sentenceCalculationType: String, eligibilityType: SDSPlusEligibilityType = SDSPlusEligibilityType.NONE): Boolean {
      return when (eligibilityType) {
        SDSPlusEligibilityType.NONE -> from(sentenceCalculationType).sdsPlusEligibilityType != SDSPlusEligibilityType.NONE
        SDSPlusEligibilityType.SECTION250 -> from(sentenceCalculationType).sdsPlusEligibilityType == SDSPlusEligibilityType.SECTION250
        SDSPlusEligibilityType.SDS -> from(sentenceCalculationType).sdsPlusEligibilityType == SDSPlusEligibilityType.SDS
      }
    }

    fun isSDS40Eligible(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).sentenceClazz == StandardDeterminateSentence::class.java
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
