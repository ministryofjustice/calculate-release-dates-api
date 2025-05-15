package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class Offence(
  val committedAt: LocalDate,
  val offenceCode: String? = null,
) {
  @JsonIgnore
  fun isCivilOffence(): Boolean = offenceCode != null && CIVIL_OFFENCE_CODES.contains(offenceCode)

  companion object {
    val CIVIL_OFFENCE_CODES = listOf(
      "ZZ01004",
      "ZZ01005",
      "ZZ01001",
      "ZZ01003",
      "ZZ01002",
      "ZZ01008",
      "ZZ01010",
      "XX00000-016N",
    )
  }
}
