package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

@Schema(description = "Sentence Calculation Dates")
data class SentenceCalcDates(
  val sentenceExpiryCalculatedDate: LocalDate?,
  val sentenceExpiryOverrideDate: LocalDate?,
  val automaticReleaseDate: LocalDate?, // calculated date
  val automaticReleaseOverrideDate: LocalDate?,
  val conditionalReleaseDate: LocalDate?, // calculated date
  val conditionalReleaseOverrideDate: LocalDate?,
  val nonParoleDate: LocalDate?, // calculated date
  val nonParoleOverrideDate: LocalDate?,
  val postRecallReleaseDate: LocalDate?, // calculated date
  val postRecallReleaseOverrideDate: LocalDate?,
  val licenceExpiryCalculatedDate: LocalDate?,
  val licenceExpiryOverrideDate: LocalDate?,
  val homeDetentionCurfewEligibilityCalculatedDate: LocalDate?,
  val homeDetentionCurfewEligibilityOverrideDate: LocalDate?,
  val paroleEligibilityCalculatedDate: LocalDate?,
  val paroleEligibilityOverrideDate: LocalDate?,
  val homeDetentionCurfewActualDate: LocalDate?, // manual date
  val actualParoleDate: LocalDate?, // manual date
  val releaseOnTemporaryLicenceDate: LocalDate?, // manual date
  val earlyRemovalSchemeEligibilityDate: LocalDate?, // manual date
  val tariffEarlyRemovalSchemeEligibilityDate: LocalDate?, // manual date
  val tariffDate: LocalDate?, // manual date
  val etdCalculatedDate: LocalDate?,
  val etdOverrideDate: LocalDate?,
  val mtdCalculatedDate: LocalDate?,
  val mtdOverrideDate: LocalDate?,
  val ltdCalculatedDate: LocalDate?,
  val ltdOverrideDate: LocalDate?,
  val topupSupervisionExpiryCalculatedDate: LocalDate?,
  val topupSupervisionExpiryOverrideDate: LocalDate?,
  val dtoPostRecallReleaseDate: LocalDate?, // calculated date
  val dtoPostRecallReleaseDateOverride: LocalDate?,
  val effectiveSentenceEndDate: LocalDate?,
) {

  private fun toComparableCalculatedMap(): Map<ReleaseDateType, LocalDate?> = mapOf(
    ReleaseDateType.SED to (sentenceExpiryOverrideDate ?: sentenceExpiryCalculatedDate),
    ReleaseDateType.ARD to (automaticReleaseOverrideDate ?: automaticReleaseDate),
    ReleaseDateType.CRD to (conditionalReleaseOverrideDate ?: conditionalReleaseDate),
    ReleaseDateType.NPD to (nonParoleOverrideDate ?: nonParoleDate),
    ReleaseDateType.PRRD to (postRecallReleaseOverrideDate ?: postRecallReleaseDate),
    ReleaseDateType.LED to (licenceExpiryOverrideDate ?: licenceExpiryCalculatedDate),
    ReleaseDateType.HDCED to (homeDetentionCurfewEligibilityOverrideDate ?: homeDetentionCurfewEligibilityCalculatedDate),
    ReleaseDateType.PED to (paroleEligibilityOverrideDate ?: paroleEligibilityCalculatedDate),
    ReleaseDateType.ETD to (etdOverrideDate ?: etdCalculatedDate),
    ReleaseDateType.MTD to (mtdOverrideDate ?: mtdCalculatedDate),
    ReleaseDateType.LTD to (ltdOverrideDate ?: ltdCalculatedDate),
    ReleaseDateType.TUSED to (topupSupervisionExpiryOverrideDate ?: topupSupervisionExpiryCalculatedDate),
    ReleaseDateType.DPRRD to (dtoPostRecallReleaseDateOverride ?: dtoPostRecallReleaseDate),
  )
  fun toCalculatedMap(): Map<ReleaseDateType, LocalDate?> =
    mapOf(
      ReleaseDateType.SED to sentenceExpiryCalculatedDate,
      ReleaseDateType.ARD to automaticReleaseDate,
      ReleaseDateType.CRD to conditionalReleaseDate,
      ReleaseDateType.NPD to nonParoleDate,
      ReleaseDateType.PRRD to postRecallReleaseDate,
      ReleaseDateType.LED to licenceExpiryCalculatedDate,
      ReleaseDateType.HDCED to homeDetentionCurfewEligibilityCalculatedDate,
      ReleaseDateType.PED to paroleEligibilityCalculatedDate,
      ReleaseDateType.ETD to etdCalculatedDate,
      ReleaseDateType.MTD to mtdCalculatedDate,
      ReleaseDateType.LTD to ltdCalculatedDate,
      ReleaseDateType.TUSED to topupSupervisionExpiryCalculatedDate,
      ReleaseDateType.DPRRD to dtoPostRecallReleaseDate,
      ReleaseDateType.ESED to effectiveSentenceEndDate,
    )

  fun toOverrideMap(): Map<ReleaseDateType, LocalDate?> =
    mapOf(
      ReleaseDateType.HDCED to homeDetentionCurfewEligibilityOverrideDate,
      ReleaseDateType.ETD to etdOverrideDate,
      ReleaseDateType.MTD to mtdOverrideDate,
      ReleaseDateType.LTD to ltdOverrideDate,
      ReleaseDateType.DPRRD to dtoPostRecallReleaseDateOverride,
      ReleaseDateType.ARD to automaticReleaseOverrideDate,
      ReleaseDateType.CRD to conditionalReleaseOverrideDate,
      ReleaseDateType.PED to paroleEligibilityOverrideDate,
      ReleaseDateType.NPD to nonParoleOverrideDate,
      ReleaseDateType.LED to licenceExpiryOverrideDate,
      ReleaseDateType.PRRD to postRecallReleaseOverrideDate,
      ReleaseDateType.SED to sentenceExpiryOverrideDate,
      ReleaseDateType.TUSED to topupSupervisionExpiryOverrideDate,
      ReleaseDateType.HDCAD to homeDetentionCurfewActualDate,
      ReleaseDateType.APD to actualParoleDate,
      ReleaseDateType.ROTL to releaseOnTemporaryLicenceDate,
      ReleaseDateType.ERSED to earlyRemovalSchemeEligibilityDate,
      ReleaseDateType.TERSED to tariffEarlyRemovalSchemeEligibilityDate,
      ReleaseDateType.Tariff to tariffDate,
    )

  fun isSameComparableCalculatedDates(other: SentenceCalcDates): Boolean =
    toComparableCalculatedMap() == other.toComparableCalculatedMap()
}
