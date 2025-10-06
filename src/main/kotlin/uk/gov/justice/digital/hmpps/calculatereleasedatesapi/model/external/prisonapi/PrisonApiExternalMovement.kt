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
  val movementDate: LocalDate?,
  val movementTime: LocalTime?,
  val movementReason: String?,
  val movementReasonCode: String?,
  val fromAgency: String?,
  val commentText: String?,
) {

  fun transformMovementReason(): ExternalMovementReason? = if (directionCode == "IN" && (isFromIRC() || isFromImmigrationCourt())) {
    ExternalMovementReason.FAILED_ERS_REMOVAL
  } else {
    when (movementReasonCode) {
      "CE", "AR", "FR1", "FR2", "CR" -> ExternalMovementReason.CRD
      "ECSLIRC", "ECSL" -> ExternalMovementReason.ECSL
      "DE", "DEIRC", "DL", "DD", "ETR" -> ExternalMovementReason.ERS
      "HR", "HU", "HD", "HE" -> ExternalMovementReason.HDC
      "BD" -> ExternalMovementReason.IMMIGRATION_BAIL
      "PX" -> ExternalMovementReason.PAROLE
      "D1", "D2" -> ExternalMovementReason.DTO
      "PD" -> ExternalMovementReason.RECALL_RELEASE

      "L" -> ExternalMovementReason.RECALL_ADMISSION
      "I", "W", "25" -> ExternalMovementReason.SENTENCE
      "V", "N" -> ExternalMovementReason.REMAND
      "B" -> ExternalMovementReason.HDC_RECALL
      "ETB" -> ExternalMovementReason.ERS_BREACH
      else -> null
    }
  }

  private fun isFromIRC(): Boolean = (fromAgency ?: "").contains("IRC")

  private fun isFromImmigrationCourt(): Boolean = (fromAgency ?: "") == "IMM"

  fun transformMovementDirection(): ExternalMovementDirection = if (directionCode == "OUT") {
    ExternalMovementDirection.OUT
  } else {
    ExternalMovementDirection.IN
  }
}
