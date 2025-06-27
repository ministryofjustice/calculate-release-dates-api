package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.AFine
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.Botus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.DetentionAndTrainingOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.ExtendedDeterminate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.Indeterminate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.Sopc
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.StandardDeterminate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL_255

private val UNIMPLEMENTED_SENTENCE_TYPE = null

// These SentenceCalculationType values come from NOMIS - they map to offender_sentences.sentence_calc_type in NOMIS
enum class SentenceCalculationType(
  val sentenceType: SentenceType?,
  val recallType: RecallType? = null,
  val primaryName: String? = null,
  val toreraEligibilityType: ToreraEligibilityType = ToreraEligibilityType.NONE,
  val sdsPlusEligibilityType: SDSPlusEligibilityType = SDSPlusEligibilityType.NONE,
  val displayName: String? = null,
) {
  //region SDS / ORA Sentences
  ADIMP(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  ADIMP_ORA(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  SEC91_03(sentenceType = StandardDeterminate, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  SEC91_03_ORA(sentenceType = StandardDeterminate, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  SEC250(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  SEC250_ORA(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SECTION250),
  YOI(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS, displayName = "Young offender institution"),
  YOI_ORA(sentenceType = StandardDeterminate, toreraEligibilityType = ToreraEligibilityType.SDS, sdsPlusEligibilityType = SDSPlusEligibilityType.SDS),
  //endregion

  //region Extended Determinate Sentences
  EDS18(sentenceType = ExtendedDeterminate),
  EDS21(sentenceType = ExtendedDeterminate),
  EDSU18(sentenceType = ExtendedDeterminate),
  LASPO_AR(sentenceType = ExtendedDeterminate),
  LASPO_DR(sentenceType = ExtendedDeterminate),
  //endregion

  //region SOPC Sentences
  SDOPCU18(sentenceType = Sopc),
  SOPC18(sentenceType = Sopc, toreraEligibilityType = ToreraEligibilityType.SOPC),
  SOPC21(sentenceType = Sopc, toreraEligibilityType = ToreraEligibilityType.SOPC),
  SEC236A(sentenceType = Sopc, toreraEligibilityType = ToreraEligibilityType.SOPC),
  //endregion

  //region Fine, BOTUS and DTO Sentences
  AFINE(sentenceType = AFine, primaryName = "A/FINE", displayName = "Imprisoned in default of a fine"),
  DTO(sentenceType = DetentionAndTrainingOrder, displayName = "Detention and training order"),
  DTO_ORA(sentenceType = DetentionAndTrainingOrder),
  BOTUS(sentenceType = Botus),
  //endregion

  //region Standard Recall Sentences
  LR(sentenceType = StandardDeterminate, recallType = STANDARD_RECALL),
  LR_ORA(sentenceType = StandardDeterminate, recallType = STANDARD_RECALL),
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
  LR_YOI_ORA(sentenceType = StandardDeterminate, recallType = STANDARD_RECALL),
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
  ALP_LASPO(sentenceType = Indeterminate),
  DLP(sentenceType = Indeterminate, displayName = "Adult discretionary life"),
  ALP(sentenceType = Indeterminate, displayName = "Automatic life"),
  ALP_CODE18(sentenceType = Indeterminate, displayName = "Automatic life sec 273 sentencing code (18 - 20 years old)"),
  ALP_CODE21(sentenceType = Indeterminate, displayName = "Automatic life sec 283 sentencing code (21+)"),
  DFL(sentenceType = Indeterminate, displayName = "Detention for life"),
  DPP(sentenceType = Indeterminate, displayName = "Detention for public protection"),
  HMPL(sentenceType = Indeterminate, displayName = "Detention during His Majesty's pleasure"),
  IPP(sentenceType = Indeterminate, displayName = "Indeterminate sentence for the public protection"),
  LEGACY(sentenceType = Indeterminate, displayName = "Legacy (pre 1991 Act)"),
  LIFE(sentenceType = Indeterminate, displayName = "Life imprisonment or detention S.53(1) CYPA 1933"),
  LIFE_IPP(sentenceType = Indeterminate, primaryName = "LIFE/IPP", displayName = "Life or indeterminate sentence for public protection"),
  MLP(sentenceType = Indeterminate, displayName = "Adult mandatory life"),
  SEC272(sentenceType = Indeterminate, displayName = "Custody for life sec 272 sentencing code (18 - 20 years old)"),
  SEC275(sentenceType = Indeterminate, displayName = "Custody for life Sec 275 sentencing code (murder) (Under 21 years old)"),
  SEC93_03(sentenceType = Indeterminate, displayName = "Custody for life under 21 years old CJA03"),
  SEC94(sentenceType = Indeterminate, displayName = "Custody life (18-21 years old)"),
  ZMD(sentenceType = Indeterminate),
  SEC93(sentenceType = Indeterminate),
  TWENTY(sentenceType = Indeterminate, primaryName = "20"),
  //endregion

  //region Indeterminate Recall Sentences
  LR_ALP(sentenceType = Indeterminate, recallType = STANDARD_RECALL, displayName = "Recall from automatic life"),
  LR_ALP_CDE18(sentenceType = Indeterminate, recallType = STANDARD_RECALL),
  LR_ALP_CDE21(sentenceType = Indeterminate, recallType = STANDARD_RECALL, displayName = "Recall from automatic life sec 283 sentencing code (21+)"),
  LR_ALP_LASPO(sentenceType = Indeterminate, recallType = STANDARD_RECALL, displayName = "Automatic life sec 224A 03"),
  LR_DLP(sentenceType = Indeterminate, recallType = STANDARD_RECALL, displayName = "Recall from discretionary life"),
  LR_DPP(sentenceType = Indeterminate, recallType = STANDARD_RECALL, displayName = "Licence recall from DPP sentence"),
  LR_IPP(sentenceType = Indeterminate, recallType = STANDARD_RECALL, displayName = "Licence recall from IPP sentence"),
  LR_LIFE(sentenceType = Indeterminate, recallType = STANDARD_RECALL, displayName = "Recall to custody indeterminate sentence"),
  LR_MLP(sentenceType = Indeterminate, recallType = STANDARD_RECALL, displayName = "Recall to custody mandatory life"),
  //endregion

  //region UNSUPPORTED(null) Sentence Types
  NP(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, displayName = "Adult imprisonment less than 12 months"),
  CR(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, displayName = "Adult imprisonment above 12 months below 4 years"),
  AR(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE),
  EPP(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE),
  CIVIL(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, displayName = "Civil imprisonment"),
  EXT(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, displayName = "Extended sec 86 of PCC(S) act 2000"),
  SEC91(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, displayName = "Serious offence under 18 POCCA 2000"),
  VOO(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE),
  STS18(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE),
  STS21(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, displayName = "Serious terrorism sentence sec 282A (21+)"),
  TISCS(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, displayName = "Term of imprisonment, intensive supervision court sanction - schedule 10, sentencing act 2020"),
  YRO(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, displayName = "Youth rehabilitation order"),
  //endregion

  //region UNSUPPORTED(null) Recall Sentences
  FTR_HDC(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, recallType = FIXED_TERM_RECALL_14, displayName = "Fixed term recall while on HDC"),
  LR_ES(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, recallType = STANDARD_RECALL, displayName = "Licence recall from extended sentence"),
  LR_EPP(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, recallType = STANDARD_RECALL, displayName = "Licence recall from extended sentence for public protection"),
  FTR_HDC_ORA(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, recallType = FIXED_TERM_RECALL_28, displayName = "ORA fixed term recall while on HDC"),
  FTR_14_HDC_ORA(
    sentenceType = UNIMPLEMENTED_SENTENCE_TYPE,
    recallType = FIXED_TERM_RECALL_14,
    primaryName = "14FTRHDC_ORA",
    displayName = "14 day fixed term recall from HDC",
  ),
  HDR_ORA(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, recallType = STANDARD_RECALL_255, displayName = "ORA HDC recall (not curfew violation)"),
  HDR(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, recallType = STANDARD_RECALL_255, displayName = "Inability to monitor"),
  CUR(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, recallType = STANDARD_RECALL_255, displayName = "Breach of curfew"),
  CUR_ORA(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE, recallType = STANDARD_RECALL_255, displayName = "ORA recalled from curfew conditions"),
  //endregion

  //region Unidentified
  UNIDENTIFIED(sentenceType = UNIMPLEMENTED_SENTENCE_TYPE),
  //endregion
  ;

  companion object {

    fun from(sentenceCalculationType: String): SentenceCalculationType = entries.firstOrNull { it.primaryName == sentenceCalculationType }
      ?: entries.firstOrNull { it.name == sentenceCalculationType }
      ?: UNIDENTIFIED

    fun displayName(sentence: SentenceAndOffence): String {
      val type = from(sentence.sentenceCalculationType)
      return type.displayName ?: "${sentence.sentenceCategory} ${sentence.sentenceTypeDescription}"
    }

    fun isCalculable(sentenceCalculationType: String): Boolean = !(isIndeterminate(sentenceCalculationType) || isUnimplementedSentence(sentenceCalculationType))

    fun isIndeterminate(sentenceCalculationType: String): Boolean = from(sentenceCalculationType).sentenceType == Indeterminate

    private fun isUnimplementedSentence(sentenceCalculationType: String): Boolean = from(sentenceCalculationType).sentenceType == UNIMPLEMENTED_SENTENCE_TYPE

    fun isSDS40Eligible(sentenceCalculationType: String): Boolean = from(sentenceCalculationType).sentenceType == StandardDeterminate

    fun isDTOType(sentenceCalculationType: String): Boolean = from(sentenceCalculationType).sentenceType == DetentionAndTrainingOrder

    fun isSDSPlusEligible(
      sentenceCalculationType: String,
      eligibilityType: SDSPlusEligibilityType = SDSPlusEligibilityType.NONE,
    ): Boolean = when (eligibilityType) {
      SDSPlusEligibilityType.NONE -> from(sentenceCalculationType).sdsPlusEligibilityType != SDSPlusEligibilityType.NONE
      SDSPlusEligibilityType.SECTION250 -> from(sentenceCalculationType).sdsPlusEligibilityType == SDSPlusEligibilityType.SECTION250
      SDSPlusEligibilityType.SDS -> from(sentenceCalculationType).sdsPlusEligibilityType == SDSPlusEligibilityType.SDS
    }

    fun isToreraEligible(sentenceCalculationType: String, eligibilityType: ToreraEligibilityType): Boolean = try {
      from(sentenceCalculationType).toreraEligibilityType == eligibilityType
    } catch (_: IllegalArgumentException) {
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
