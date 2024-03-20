package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.time.LocalDateTime

data class OffenderKeyDates(
  val reasonCode: String,
  val calculatedAt: LocalDateTime,
  val comment: String? = null,
  val homeDetentionCurfewEligibilityDate: LocalDate? = null,
  val earlyTermDate: LocalDate? = null,
  val midTermDate: LocalDate? = null,
  val lateTermDate: LocalDate? = null,
  val dtoPostRecallReleaseDate: LocalDate? = null,
  val automaticReleaseDate: LocalDate? = null,
  val conditionalReleaseDate: LocalDate? = null,
  val paroleEligibilityDate: LocalDate? = null,
  val nonParoleDate: LocalDate? = null,
  val licenceExpiryDate: LocalDate? = null,
  val postRecallReleaseDate: LocalDate? = null,
  val sentenceExpiryDate: LocalDate? = null,
  val topupSupervisionExpiryDate: LocalDate? = null,
  val earlyRemovalSchemeEligibilityDate: LocalDate? = null,
  val effectiveSentenceEndDate: LocalDate? = null,
  val sentenceLength: String? = null,
  val homeDetentionCurfewApprovedDate: LocalDate? = null,
  val tariffDate: LocalDate? = null,
  val tariffExpiredRemovalSchemeEligibilityDate: LocalDate? = null,
  val approvedParoleDate: LocalDate? = null,
  val releaseOnTemporaryLicenceDate: LocalDate? = null,
  val judiciallyImposedSentenceLength: String? = null,
)
