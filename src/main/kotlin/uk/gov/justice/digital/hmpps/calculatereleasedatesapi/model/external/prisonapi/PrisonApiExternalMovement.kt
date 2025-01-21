package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class PrisonApiExternalMovement(
  val offenderNo: String,
  val createDateTime: LocalDateTime?,
  val movementType: String,
  val movementTypeDescription: String?,
  val directionCode: String,
  val movementDate: LocalDate,
  val movementTime: LocalTime?,
  val movementReason: String?,
  val movementReasonCode: String,
  val commentText: String?,
) {

  fun transformMovementReason(): ExternalMovementReason? {
    return when (movementReasonCode) {
      "HR", "HU" -> ExternalMovementReason.HDC
      "DE", "DEIRC", "DL", "DD", "ETR" -> ExternalMovementReason.ERS
      "PX" -> ExternalMovementReason.PAROLE
      "ECSLIRC", "ECSL" -> ExternalMovementReason.ECSL
      else -> null
    }
  }

  fun transformMovementDirection(): ExternalMovementDirection {
    return if (directionCode == "OUT") {
      ExternalMovementDirection.OUT
    } else {
      ExternalMovementDirection.IN
    }
  }
}
