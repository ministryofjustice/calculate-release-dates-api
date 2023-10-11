package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Sentence Calculation Dates")
data class SentenceCalcDates(
  val sentenceExpiryDate: LocalDate?,
  val automaticReleaseDate: LocalDate?,
  val conditionalReleaseDate: LocalDate?,
  val nonParoleDate: LocalDate?,
  val postRecallReleaseDate: LocalDate?,
  val licenceExpiryDate: LocalDate?,
  val homeDetentionCurfewEligibilityDate: LocalDate?,
  val paroleEligibilityDate: LocalDate?,
  val homeDetentionCurfewActualDate: LocalDate?,
  val actualParoleDate: LocalDate?,
  val releaseOnTemporaryLicenceDate: LocalDate?,
  val earlyRemovalSchemeEligibilityDate: LocalDate?,
  val earlyTermDate: LocalDate?,
  val midTermDate: LocalDate?,
  val lateTermDate: LocalDate?,
  val topupSupervisionExpiryDate: LocalDate?,
  val tariffDate: LocalDate?,
  val dtoPostRecallReleaseDate: LocalDate?,
  val tariffEarlyRemovalSchemeEligibilityDate: LocalDate?,
  val effectiveSentenceEndDate: LocalDate?,
)
