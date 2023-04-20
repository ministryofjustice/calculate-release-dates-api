package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class SentenceIdentificationTrack {

  SDS_BEFORE_CJA_LASPO,
  SDS_AFTER_CJA_LASPO,
  SDS_TWO_THIRDS_RELEASE,
  RECALL,
  EDS_AUTOMATIC_RELEASE,
  EDS_DISCRETIONARY_RELEASE,
  SOPC_PED_AT_TWO_THIRDS,
  SOPC_PED_AT_HALFWAY,
  AFINE_ARD_AT_HALFWAY,
  AFINE_ARD_AT_FULL_TERM,
  DTO_BEFORE_PCSC,
  DTO_AFTER_PCSC,
  ;

  fun calculateErsedFromHalfway(): Boolean {
    return listOf(SDS_AFTER_CJA_LASPO, SDS_AFTER_CJA_LASPO, SOPC_PED_AT_HALFWAY).contains(this)
  }

  fun calculateErsedFromTwoThirds(): Boolean {
    return listOf(SDS_TWO_THIRDS_RELEASE, EDS_AUTOMATIC_RELEASE, EDS_DISCRETIONARY_RELEASE, SOPC_PED_AT_TWO_THIRDS).contains(this)
  }
}
