package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class ExternalMovement(
  val movementDate: LocalDate,
  val movementReason: ExternalMovementReason,
  val direction: ExternalMovementDirection,
)

enum class ExternalMovementReason {
  HDC,
  ERS,
  PAROLE,
  ECSL,
  CRD,
  DTO,
  ADMISSION,
  ERS_RETURN,
}

enum class ExternalMovementDirection {
  IN,
  OUT,
}
