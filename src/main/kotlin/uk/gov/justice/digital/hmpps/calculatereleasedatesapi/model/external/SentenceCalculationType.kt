package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

// These SentenceCalculationType values come from NOMIS - they map to offender_sentences.sentence_calc_type in NOMIS
enum class SentenceCalculationType(
  val recallType: RecallType? = null,
  val sentenceClazz: Class<out AbstractSentence> = StandardDeterminateSentence::class.java,
  val primaryName: String? = null,
  val isFixedTermRecall: Boolean = false,
  val isSupported: Boolean = true,
  val isIndeterminate: Boolean = false,
) {
  ADIMP,
  ADIMP_ORA,
  YOI,
  YOI_ORA,
  SEC91_03,
  SEC91_03_ORA,
  SEC250,
  SEC250_ORA,
  LR(STANDARD_RECALL),
  LR_ORA(STANDARD_RECALL),
  LR_YOI_ORA(STANDARD_RECALL),
  LR_SEC91_ORA(STANDARD_RECALL),
  LRSEC250_ORA(STANDARD_RECALL),
  FTR_14_ORA(recallType = FIXED_TERM_RECALL_14, primaryName = "14FTR_ORA", isFixedTermRecall = true),
  FTR(FIXED_TERM_RECALL_28, isFixedTermRecall = true),
  FTR_ORA(FIXED_TERM_RECALL_28, isFixedTermRecall = true),
  FTR_SCH15(FIXED_TERM_RECALL_28, isFixedTermRecall = true),
  FTRSCH15_ORA(FIXED_TERM_RECALL_28, isFixedTermRecall = true),
  FTRSCH18(FIXED_TERM_RECALL_28, isFixedTermRecall = true),
  FTRSCH18_ORA(FIXED_TERM_RECALL_28, isFixedTermRecall = true),
  LASPO_AR(sentenceClazz = ExtendedDeterminateSentence::class.java),
  LASPO_DR(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDS18(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDS21(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDSU18(sentenceClazz = ExtendedDeterminateSentence::class.java),
  SDOPCU18(sentenceClazz = SopcSentence::class.java),
  SOPC18(sentenceClazz = SopcSentence::class.java),
  SOPC21(sentenceClazz = SopcSentence::class.java),
  SEC236A(sentenceClazz = SopcSentence::class.java),
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
  IPP(isSupported = false, isIndeterminate = true),
  LIFE(isSupported = false, isIndeterminate = true),
  LIFE_IPP(isSupported = false, isIndeterminate = true, primaryName = "LIFE/IPP"),
  LR_IPP(isSupported = false, isIndeterminate = true),
  MLP(isSupported = false, isIndeterminate = true),
  DLP(isSupported = false, isIndeterminate = true),
  ALP(isSupported = false, isIndeterminate = true),
  LEGACY(isSupported = false, isIndeterminate = true),
  LR_LIFE(isSupported = false, isIndeterminate = true),
  HMPL(isSupported = false, isIndeterminate = true),
  DFL(isSupported = false, isIndeterminate = true),
  LR_ALP(isSupported = false, isIndeterminate = true),
  ALP_LASPO(isSupported = false, isIndeterminate = true),
  LR_DLP(isSupported = false, isIndeterminate = true),
  LR_MLP(isSupported = false, isIndeterminate = true),
  SEC94(isSupported = false, isIndeterminate = true),
  SEC93_03(isSupported = false, isIndeterminate = true),
  ALP_CODE18(isSupported = false, isIndeterminate = true),
  DPP(isSupported = false, isIndeterminate = true),
  SEC272(isSupported = false, isIndeterminate = true),
  SEC275(isSupported = false, isIndeterminate = true),
  ALP_CODE21(isSupported = false, isIndeterminate = true),
  LR_DPP(isSupported = false, isIndeterminate = true),
  LR_ALP_CDE21(isSupported = false, isIndeterminate = true),
  LR_ALP_LASPO(isSupported = false, isIndeterminate = true),
  ZMD(isSupported = false, isIndeterminate = true),
  SEC93(isSupported = false, isIndeterminate = true),
  TWENTY(isSupported = false, isIndeterminate = true, primaryName = "20"),
  NP(isSupported = false, isIndeterminate = false),
  LR_EPP(isSupported = false, isIndeterminate = false),
  CR(isSupported = false, isIndeterminate = false),
  BOTUS(isSupported = false, isIndeterminate = false),
  AR(isSupported = false, isIndeterminate = false),
  EPP(isSupported = false, isIndeterminate = false),
  CUR_ORA(isSupported = false, isIndeterminate = false),
  A_FINE(isSupported = false, isIndeterminate = false, primaryName = "A/FINE"),
  CUR(isSupported = false, isIndeterminate = false),
  CIVIL(isSupported = false, isIndeterminate = false),
  EXT(isSupported = false, isIndeterminate = false),
  HDR_ORA(isSupported = false, isIndeterminate = false),
  HDR(isSupported = false, isIndeterminate = false),
  LR_ES(isSupported = false, isIndeterminate = false),
  FTR_HDC(isSupported = false, isIndeterminate = false),
  YRO(isSupported = false, isIndeterminate = false),
  SEC91(isSupported = false, isIndeterminate = false),
  VOO(isSupported = false, isIndeterminate = false),
  ;

  companion object {
    fun from(sentenceCalculationType: String): SentenceCalculationType =
      values().firstOrNull { it.primaryName == sentenceCalculationType } ?: valueOf(sentenceCalculationType)

    fun isSupported(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).isSupported
      } catch (error: IllegalArgumentException) {
        false
      }

    fun isIndeterminate(sentenceCalculationType: String): Boolean =
      try {
        from(sentenceCalculationType).isIndeterminate
      } catch (error: IllegalArgumentException) {
        false
      }
  }
}
