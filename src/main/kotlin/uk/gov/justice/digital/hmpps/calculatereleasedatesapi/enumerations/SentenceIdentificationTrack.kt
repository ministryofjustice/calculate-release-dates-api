package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.Constants.FULL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.Constants.HALF
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.Constants.TWO_THIRDS

enum class SentenceIdentificationTrack(
  private val multiplier: Double?,
) {

  // SDS_BEFORE_CJA_LASPO, SDS_AFTER_CJA_LASPO & SDS_TWO_THIRDS_RELEASE deprecated for SDS_STANDARD_RELEASE, SDS_EARLY_RELEASE & SDS_PLUS_RELEASE
  SDS_BEFORE_CJA_LASPO(HALF),
  SDS_AFTER_CJA_LASPO(HALF),
  SDS_TWO_THIRDS_RELEASE(TWO_THIRDS),
  SDS_STANDARD_RELEASE(HALF),
  SDS_EARLY_RELEASE(null),
  SDS_PLUS_RELEASE(TWO_THIRDS),
  SDS_STANDARD_RELEASE_T3_EXCLUSION(null),
  EDS_AUTOMATIC_RELEASE(TWO_THIRDS),
  EDS_DISCRETIONARY_RELEASE(FULL),
  SOPC_PED_AT_TWO_THIRDS(FULL),
  SOPC_PED_AT_HALFWAY(FULL),
  AFINE_ARD_AT_HALFWAY(HALF),
  AFINE_ARD_AT_FULL_TERM(FULL),
  DTO_BEFORE_PCSC(HALF),
  DTO_AFTER_PCSC(HALF),
  BOTUS(FULL),
  BOTUS_WITH_HISTORIC_TUSED(FULL),
  ;

  /**
   * Is the multiplier always the same for this identification track
   */
  fun isMultiplierFixed(): Boolean = multiplier != null
  fun fixedMultiplier(): Double = multiplier!!

  fun calculateErsed(): Boolean = listOf(
    SDS_AFTER_CJA_LASPO,
    SOPC_PED_AT_HALFWAY,
    SDS_TWO_THIRDS_RELEASE,
    EDS_AUTOMATIC_RELEASE,
    EDS_DISCRETIONARY_RELEASE,
    SOPC_PED_AT_TWO_THIRDS,
    SDS_STANDARD_RELEASE,
    SDS_EARLY_RELEASE,
    SDS_PLUS_RELEASE,
    SDS_STANDARD_RELEASE_T3_EXCLUSION,
  ).contains(this)

  fun isEarlyReleaseTrancheOneTwo(): Boolean = listOf(SDS_STANDARD_RELEASE_T3_EXCLUSION, SDS_EARLY_RELEASE).contains(this)
}
