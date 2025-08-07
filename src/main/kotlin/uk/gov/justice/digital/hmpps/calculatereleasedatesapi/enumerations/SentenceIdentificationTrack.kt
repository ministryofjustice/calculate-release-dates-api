package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.Constants.FULL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.Constants.HALF
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.Constants.TWO_THIRDS

enum class SentenceIdentificationTrack(
  private val multiplier: Double?,
) {

  SDS(null),
  SDS_PLUS(null),
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
    SOPC_PED_AT_HALFWAY,
    EDS_AUTOMATIC_RELEASE,
    EDS_DISCRETIONARY_RELEASE,
    SOPC_PED_AT_TWO_THIRDS,
    SDS,
    SDS_PLUS,
  ).contains(this)
}
