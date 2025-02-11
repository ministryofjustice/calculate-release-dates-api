package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.AFine
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.Botus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.DetentionAndTrainingOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.ExtendedDeterminate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.Indeterminate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.Sopc
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.StandardDeterminate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.Unsupported
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL_255

// These SentenceCalculationType values come from NOMIS - they map to offender_sentences.sentence_calc_type in NOMIS
enum class SentenceCalculationType(
  val sentenceType: SentenceType,
  val recallType: RecallType? = null,
  val primaryName: String? = null,
  val toreraEligibilityType: ToreraEligibilityType = ToreraEligibilityType.NONE,
  val sdsPlusEligibilityType: SDSPlusEligibilityType = SDSPlusEligibilityType.NONE,
) {
  //region SDS / ORA Sentences
  ADIMP(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  ADIMP_ORA(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  YOI(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  YOI_ORA(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  SEC250(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  SEC250_ORA(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  SEC91_03(sentenceType = StandardDeterminate),
  SEC91_03_ORA(sentenceType = StandardDeterminate),
  //endregion

  //region Extended Determinate Sentences
  LASPO_AR(sentenceType = ExtendedDeterminate),
  LASPO_DR(sentenceType = ExtendedDeterminate),
  EDS18(sentenceType = ExtendedDeterminate),
  EDS21(sentenceType = ExtendedDeterminate),
  EDSU18(sentenceType = ExtendedDeterminate),
  //endregion

  //region SOPC Sentences
  SDOPCU18(sentenceType = Sopc),
  SOPC18(sentenceType = Sopc, toreraEligibilityType = ToreraEligibilityType.SOPC),
  SOPC21(sentenceType = Sopc, toreraEligibilityType = ToreraEligibilityType.SOPC),
  SEC236A(sentenceType = Sopc, toreraEligibilityType = ToreraEligibilityType.SOPC),
  //endregion

  //region Fine, BOTUS and DTO Sentences
  AFINE(sentenceType = AFine, primaryName = "A/FINE"),
  DTO(sentenceType = DetentionAndTrainingOrder),
  DTO_ORA(sentenceType = DetentionAndTrainingOrder),
  BOTUS(sentenceType = Botus),
  //endregion

  //region Standard Recall Sentences
  LR(sentenceType = StandardDeterminate, recallType = STANDARD_RECALL),
  LR_ORA(sentenceType = StandardDeterminate, recallType = STANDARD_RECALL),
  LR_YOI_ORA(sentenceType = StandardDeterminate, recallType = STANDARD_RECALL),
  LR_SEC91_ORA(sentenceType = StandardDeterminate, recallType = STANDARD_RECALL),
  LRSEC250_ORA(sentenceType = StandardDeterminate, recallType = STANDARD_RECALL),
  LR_EDS18(sentenceType = ExtendedDeterminate, recallType = STANDARD_RECALL),
  LR_EDS21(sentenceType = ExtendedDeterminate, recallType = STANDARD_RECALL),
  LR_EDSU18(sentenceType = ExtendedDeterminate, recallType = STANDARD_RECALL),
  LR_LASPO_AR(sentenceType = ExtendedDeterminate, recallType = STANDARD_RECALL),
  LR_LASPO_DR(sentenceType = ExtendedDeterminate, recallType = STANDARD_RECALL),
  LR_SEC236A(sentenceType = Sopc, recallType = STANDARD_RECALL),
  LR_SOPC18(sentenceType = Sopc, recallType = STANDARD_RECALL),
  LR_SOPC21(sentenceType = Sopc, recallType = STANDARD_RECALL),
  //endregion

  //region Fixed Term Recall Sentences
  FTR_14_ORA(sentenceType = StandardDeterminate, recallType = FIXED_TERM_RECALL_14, primaryName = "14FTR_ORA"),
  FTR(sentenceType = StandardDeterminate, recallType = FIXED_TERM_RECALL_28),
  FTR_ORA(sentenceType = StandardDeterminate, recallType = FIXED_TERM_RECALL_28),
  FTR_SCH15(sentenceType = StandardDeterminate, recallType = FIXED_TERM_RECALL_28),
  FTRSCH15_ORA(sentenceType = StandardDeterminate, recallType = FIXED_TERM_RECALL_28),
  FTRSCH18(sentenceType = StandardDeterminate, recallType = FIXED_TERM_RECALL_28),
  FTRSCH18_ORA(sentenceType = StandardDeterminate, recallType = FIXED_TERM_RECALL_28),
  //endregion

  //region Indeterminate Sentences
  IPP(sentenceType = Indeterminate),
  LIFE(sentenceType = Indeterminate),
  LIFE_IPP(sentenceType = Indeterminate, primaryName = "LIFE/IPP"),
  MLP(sentenceType = Indeterminate),
  DLP(sentenceType = Indeterminate),
  ALP(sentenceType = Indeterminate),
  LEGACY(sentenceType = Indeterminate),
  HMPL(sentenceType = Indeterminate),
  DFL(sentenceType = Indeterminate),
  ALP_LASPO(sentenceType = Indeterminate),
  SEC94(sentenceType = Indeterminate),
  SEC93_03(sentenceType = Indeterminate),
  ALP_CODE18(sentenceType = Indeterminate),
  DPP(sentenceType = Indeterminate),
  SEC272(sentenceType = Indeterminate),
  SEC275(sentenceType = Indeterminate),
  ALP_CODE21(sentenceType = Indeterminate),
  ZMD(sentenceType = Indeterminate),
  SEC93(sentenceType = Indeterminate),
  TWENTY(sentenceType = Indeterminate, primaryName = "20"),
  //endregion

  //region Indeterminate Recall Sentences
  LR_IPP(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_LIFE(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_ALP(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_DLP(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_MLP(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_DPP(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_ALP_CDE18(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_ALP_CDE21(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_ALP_LASPO(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  //endregion

  //region UnsupportedSentence Sentence Types
  NP(sentenceType = Unsupported),
  CR(sentenceType = Unsupported),
  AR(sentenceType = Unsupported),
  EPP(sentenceType = Unsupported),
  A_FINE(sentenceType = Unsupported, primaryName = "A/FINE"),
  CIVIL(sentenceType = Unsupported),
  EXT(sentenceType = Unsupported),
  YRO(sentenceType = Unsupported),
  SEC91(sentenceType = Unsupported),
  VOO(sentenceType = Unsupported),
  TISCS(sentenceType = Unsupported),
  STS21(sentenceType = Unsupported),
  STS18(sentenceType = Unsupported),
  //endregion

  //region UnsupportedSentence Recall Sentences
  FTR_HDC(sentenceType = Unsupported, recallType = FIXED_TERM_RECALL_14),
  LR_ES(sentenceType = Unsupported, recallType = STANDARD_RECALL),
  LR_EPP(sentenceType = Unsupported, recallType = STANDARD_RECALL),
  FTR_HDC_ORA(recallType = FIXED_TERM_RECALL_28, sentenceType = Unsupported),
  FTR_14_HDC_ORA(
    sentenceType = Unsupported,
    recallType = FIXED_TERM_RECALL_14,
    primaryName = "14FTRHDC_ORA",
  ),
  HDR_ORA(sentenceType = Unsupported, recallType = STANDARD_RECALL_255),
  HDR(sentenceType = Unsupported, recallType = STANDARD_RECALL_255),
  CUR(sentenceType = Unsupported, recallType = STANDARD_RECALL_255),
  CUR_ORA(sentenceType = Unsupported, recallType = STANDARD_RECALL_255),
  //endregion

  //region Unidentified
  UNIDENTIFIED(sentenceType = Unsupported),
  //endregion
  ;

  companion object {

    fun from(sentenceCalculationType: String): SentenceCalculationType =
      entries.firstOrNull { it.primaryName == sentenceCalculationType }
        ?: entries.firstOrNull { it.name == sentenceCalculationType }
        ?: UNIDENTIFIED

    fun isSupported(sentenceCalculationType: String): Boolean =
      !(isIndeterminate(sentenceCalculationType) || isUnsupportedSentence(sentenceCalculationType))

    fun isIndeterminate(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).sentenceType == Indeterminate
      } catch (error: IllegalArgumentException) {
        false
      }

    private fun isUnsupportedSentence(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).sentenceType == Unsupported
      } catch (error: IllegalArgumentException) {
        false
      }

    fun isSDSPlusEligible(
      sentenceCalculationType: String,
      eligibilityType: SDSPlusEligibilityType = SDSPlusEligibilityType.NONE,
    ): Boolean {
      return when (eligibilityType) {
        SDSPlusEligibilityType.NONE -> from(sentenceCalculationType).sdsPlusEligibilityType != SDSPlusEligibilityType.NONE
        SDSPlusEligibilityType.SECTION250 -> from(sentenceCalculationType).sdsPlusEligibilityType == SDSPlusEligibilityType.SECTION250
        SDSPlusEligibilityType.SDS -> from(sentenceCalculationType).sdsPlusEligibilityType == SDSPlusEligibilityType.SDS
      }
    }

    fun isSDS40Eligible(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).sentenceType == StandardDeterminate
      } catch (error: IllegalArgumentException) {
        false
      }

    fun isToreraEligible(sentenceCalculationType: String, eligibilityType: ToreraEligibilityType): Boolean =
      try {
        from(sentenceCalculationType).toreraEligibilityType == eligibilityType
      } catch (error: IllegalArgumentException) {
        false
      }

    fun isDTOType(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).sentenceType == DetentionAndTrainingOrder
      } catch (error: IllegalArgumentException) {
        false
      }
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
