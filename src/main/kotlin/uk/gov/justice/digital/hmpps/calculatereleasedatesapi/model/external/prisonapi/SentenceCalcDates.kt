package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
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
) {

  fun toMap(): Map<ReleaseDateType, LocalDate?> =
    mapOf(
      ReleaseDateType.SED to sentenceExpiryDate,
      ReleaseDateType.ARD to automaticReleaseDate,
      ReleaseDateType.CRD to conditionalReleaseDate,
      ReleaseDateType.NPD to nonParoleDate,
      ReleaseDateType.PRRD to postRecallReleaseDate,
      ReleaseDateType.LED to licenceExpiryDate,
      ReleaseDateType.HDCED to homeDetentionCurfewEligibilityDate,
      ReleaseDateType.PED to paroleEligibilityDate,
      ReleaseDateType.HDCAD to homeDetentionCurfewActualDate,
      ReleaseDateType.APD to actualParoleDate,
      ReleaseDateType.ROTL to releaseOnTemporaryLicenceDate,
      ReleaseDateType.ERSED to earlyRemovalSchemeEligibilityDate,
      ReleaseDateType.ETD to earlyTermDate,
      ReleaseDateType.MTD to midTermDate,
      ReleaseDateType.LTD to lateTermDate,
      ReleaseDateType.TUSED to topupSupervisionExpiryDate,
      ReleaseDateType.Tariff to tariffDate,
      ReleaseDateType.DPRRD to dtoPostRecallReleaseDate,
      ReleaseDateType.TERSED to tariffEarlyRemovalSchemeEligibilityDate,
      ReleaseDateType.ESED to effectiveSentenceEndDate,
    )
}
