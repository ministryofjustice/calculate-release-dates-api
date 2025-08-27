package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class ExternalMovement(
  val movementDate: LocalDate,
  val movementReason: ExternalMovementReason,
  val direction: ExternalMovementDirection,
)

enum class ExternalMovementReason {
  // Releases
  HDC,
  ERS,
  PAROLE,
  ECSL,
  CRD,
  DTO,
  IMMIGRATION_BAIL,

  // Admissions
  SENTENCE,
  REMAND,
  RECALL,
  HDC_RECALL,
  ERS_BREACH,
  FAILED_ERS_REMOVAL,
}

enum class ExternalMovementDirection {
  IN,
  OUT,
}
