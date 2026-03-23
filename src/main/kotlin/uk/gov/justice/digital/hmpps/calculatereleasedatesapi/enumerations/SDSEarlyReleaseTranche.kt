package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class SDSEarlyReleaseTranche(val category: SDSEarlyReleaseTrancheCategory) {
  TRANCHE_0(SDSEarlyReleaseTrancheCategory.SDS40),
  TRANCHE_1(SDSEarlyReleaseTrancheCategory.SDS40),
  TRANCHE_2(SDSEarlyReleaseTrancheCategory.SDS40),

  FTR_56_TRANCHE_0(SDSEarlyReleaseTrancheCategory.FTR56),
  FTR_56_TRANCHE_1(SDSEarlyReleaseTrancheCategory.FTR56),
  FTR_56_TRANCHE_2(SDSEarlyReleaseTrancheCategory.FTR56),
  FTR_56_TRANCHE_3(SDSEarlyReleaseTrancheCategory.FTR56),
  FTR_56_TRANCHE_4(SDSEarlyReleaseTrancheCategory.FTR56),
  FTR_56_TRANCHE_5(SDSEarlyReleaseTrancheCategory.FTR56),
  FTR_56_TRANCHE_6(SDSEarlyReleaseTrancheCategory.FTR56),
}

enum class SDSEarlyReleaseTrancheCategory {
  SDS40,
  FTR56,
}
