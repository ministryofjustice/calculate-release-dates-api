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
  val fromAgency: String?,
  val commentText: String?,
) {

  fun transformMovementReason(): ExternalMovementReason? = when (movementReasonCode) {
    "HR", "HU" -> ExternalMovementReason.HDC
    "DE", "DEIRC", "DL", "DD", "ETR" -> ExternalMovementReason.ERS
    "PX" -> ExternalMovementReason.PAROLE
    "ECSLIRC", "ECSL" -> ExternalMovementReason.ECSL
    "CR", "AR", "CE" -> ExternalMovementReason.CRD
    "D3", "D2", "D1", "D4" -> ExternalMovementReason.DTO
    "E", "R", "I", "T" -> getExternalMovementReasonForErsAdmission()
    "N", "L", "V", "W", "G", "B", "25", "Y", "F", "C", "27", "26", "ETRLR", "ETB", "29", "ELR" -> ExternalMovementReason.ADMISSION
    else -> null
  }

  private fun getExternalMovementReasonForErsAdmission(): ExternalMovementReason = if (isFromIRC() || isFromImmigrationCourt()) {
    ExternalMovementReason.ERS_RETURN
  } else {
    ExternalMovementReason.ADMISSION
  }

  private fun isFromIRC(): Boolean = (fromAgency ?: "").contains("IRC")

  private fun isFromImmigrationCourt(): Boolean = (fromAgency ?: "") == "IMM"

  fun transformMovementDirection(): ExternalMovementDirection = if (directionCode == "OUT") {
    ExternalMovementDirection.OUT
  } else {
    ExternalMovementDirection.IN
  }
}
