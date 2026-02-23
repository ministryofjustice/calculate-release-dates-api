package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier

enum class SentenceIdentificationTrack(
  private val multiplier: ReleaseMultiplier?,
) {

  SDS(null),
  SDS_PLUS(null),
  EDS_AUTOMATIC_RELEASE(ReleaseMultiplier.TWO_THIRDS),
  EDS_DISCRETIONARY_RELEASE(ReleaseMultiplier.FULL),
  SOPC_PED_AT_TWO_THIRDS(ReleaseMultiplier.FULL),
  SOPC_PED_AT_HALFWAY(ReleaseMultiplier.FULL),
  AFINE_ARD_AT_HALFWAY(ReleaseMultiplier.ONE_HALF),
  AFINE_ARD_AT_FULL_TERM(ReleaseMultiplier.FULL),
  DTO_BEFORE_PCSC(ReleaseMultiplier.ONE_HALF),
  DTO_AFTER_PCSC(ReleaseMultiplier.ONE_HALF),
  BOTUS(ReleaseMultiplier.FULL),
  BOTUS_WITH_HISTORIC_TUSED(ReleaseMultiplier.FULL),
  ;

  /**
   * Is the multiplier always the same for this identification track
   */
  fun isMultiplierFixed(): Boolean = multiplier != null
  fun fixedMultiplier(): ReleaseMultiplier = multiplier!!

  fun calculateErsed(): Boolean = listOf(
    SOPC_PED_AT_HALFWAY,
    EDS_AUTOMATIC_RELEASE,
    EDS_DISCRETIONARY_RELEASE,
    SOPC_PED_AT_TWO_THIRDS,
    SDS,
    SDS_PLUS,
  ).contains(this)
}
