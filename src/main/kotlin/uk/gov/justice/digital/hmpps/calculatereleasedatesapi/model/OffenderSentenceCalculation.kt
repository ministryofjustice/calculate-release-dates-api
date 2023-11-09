package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.time.LocalDateTime

data class OffenderSentenceCalculation(
  val bookingId: Long?,
  val offenderNo: String,
  val firstName: String,
  val lastName: String,
  val agencyLocationId: String,
  val offenderSentCalculationId: Long,
  val calculationDate: LocalDateTime,
  val sentenceExpiryDate: LocalDate? = null,
  val licenceExpiryDate: LocalDate? = null,
  val paroleEligibilityDate: LocalDate? = null,
  val homeDetCurfEligibilityDate: LocalDate? = null,
  val homeDetCurfActualDate: LocalDate? = null,
  val automaticReleaseDate: LocalDate? = null,
  val conditionalReleaseDate: LocalDate? = null,
  val nonParoleDate: LocalDate? = null,
  val postRecallReleaseDate: LocalDate? = null,
  val actualParolDate: LocalDate? = null,
  val topupSupervisionExpiryDate: LocalDate? = null,
  val earlyTermDate: LocalDate? = null,
  val midTermDate: LocalDate? = null,
  val lateTermDate: LocalDate? = null,
  val tariffDate: LocalDate? = null,
  val rotl: LocalDate? = null,
  val ersed: LocalDate? = null,
  val commentText: String,
)
