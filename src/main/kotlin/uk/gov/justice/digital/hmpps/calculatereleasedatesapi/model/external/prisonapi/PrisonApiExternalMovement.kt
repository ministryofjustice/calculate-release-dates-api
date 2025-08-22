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
    // TODO check this with scott.
    "E", "R", "I", "T" -> getExternalMovementReasonForErsAdmission()
    "L" -> ExternalMovementReason.RECALL
    "I", "W" -> ExternalMovementReason.SENTENCE
    "V", "N" -> ExternalMovementReason.REMAND
    "B" -> ExternalMovementReason.HDC_RECALL
    "ETB" -> ExternalMovementReason.ERS_BREACH

    "N", "L", "V", "W", "G", "B", "25", "Y", "F", "C", "27", "26", "ETRLR", "ETB", "29", "ELR" -> ExternalMovementReason.UNKNOWN_ADMISSION
    else -> null
  }

  private fun getExternalMovementReasonForErsAdmission(): ExternalMovementReason = if (isFromIRC() || isFromImmigrationCourt()) {
    ExternalMovementReason.FAILED_ERS_REMOVAL
  } else if (movementReasonCode == "I") {
    ExternalMovementReason.SENTENCE
  } else {
    ExternalMovementReason.UNKNOWN_ADMISSION
  }

  private fun isFromIRC(): Boolean = (fromAgency ?: "").contains("IRC")

  private fun isFromImmigrationCourt(): Boolean = (fromAgency ?: "") == "IMM"

  fun transformMovementDirection(): ExternalMovementDirection = if (directionCode == "OUT") {
    ExternalMovementDirection.OUT
  } else {
    ExternalMovementDirection.IN
  }
}
