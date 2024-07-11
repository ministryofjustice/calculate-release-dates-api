package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

data class SDSEarlyReleaseExclusionForOffenceCode(val offenceCode: String, val schedulePart: SDSEarlyReleaseExclusionSchedulePart)
enum class SDSEarlyReleaseExclusionSchedulePart {
  SEXUAL,
  VIOLENT,
  DOMESTIC_ABUSE,
  NATIONAL_SECURITY,
  NONE,
}
