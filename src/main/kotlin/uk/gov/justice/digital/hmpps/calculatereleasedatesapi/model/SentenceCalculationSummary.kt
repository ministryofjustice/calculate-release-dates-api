package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDateTime

data class SentenceCalculationSummary(
  val bookingId: Long,
  val offenderNo: String,
  val firstName: String?,
  val lastName: String,
  val agencyLocationId: String,
  val agencyDescription: String,
  val offenderSentCalculationId: Long,
  val calculationDate: LocalDateTime,
  val staffId: Long?,
  val commentText: String? = null,
  val calculationReason: String,
  val calculatedByUserId: String,
  // TODO nullable until Prison API released to production
  val calculatedByFirstName: String? = null,
  val calculatedByLastName: String? = null,
)
