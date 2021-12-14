package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import java.time.LocalDate

data class OffenderKeyDates(
  val conditionalReleaseDate: LocalDate? = null,
  val licenceExpiryDate: LocalDate? = null,
  val sentenceExpiryDate: LocalDate? = null,
  val automaticReleaseDate: LocalDate? = null,
  val dtoPostRecallReleaseDate: LocalDate? = null,
  val earlyTermDate: LocalDate? = null,
  val homeDetentionCurfewEligibilityDate: LocalDate? = null,
  val lateTermDate: LocalDate? = null,
  val midTermDate: LocalDate? = null,
  val nonParoleDate: LocalDate? = null,
  val paroleEligibilityDate: LocalDate? = null,
  val postRecallReleaseDate: LocalDate? = null,
  val topupSupervisionExpiryDate: LocalDate? = null,
  val effectiveSentenceEndDate: LocalDate? = null,
  val sentenceLength: String,
)
