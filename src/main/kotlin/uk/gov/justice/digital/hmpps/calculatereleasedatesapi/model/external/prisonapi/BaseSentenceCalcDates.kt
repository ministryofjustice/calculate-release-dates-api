package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

class BaseSentenceCalcDates {

  @Schema(description = "SED - date on which sentence expires.", example = "2020-02-03")
  private val sentenceExpiryDate: LocalDate? = null

  @Schema(description = "ARD - calculated automatic (unconditional) release date for offender.", example = "2020-02-03")
  private val automaticReleaseDate: LocalDate? = null

  @Schema(description = "CRD - calculated conditional release date for offender.", example = "2020-02-03")
  private val conditionalReleaseDate: LocalDate? = null

  @Schema(
    description = "NPD - calculated non-parole date for offender (relating to the 1991 act).",
    example = "2020-02-03",
  )
  private val nonParoleDate: LocalDate? = null

  @Schema(description = "PRRD - calculated post-recall release date for offender.", example = "2020-02-03")
  private val postRecallReleaseDate: LocalDate? = null

  @Schema(description = "LED - date on which offender licence expires.", example = "2020-02-03")
  private val licenceExpiryDate: LocalDate? = null

  @Schema(
    description = "HDCED - date on which offender will be eligible for home detention curfew.",
    example = "2020-02-03",
  )
  private val homeDetentionCurfewEligibilityDate: LocalDate? = null

  @Schema(description = "PED - date on which offender is eligible for parole.", example = "2020-02-03")
  private val paroleEligibilityDate: LocalDate? = null

  @Schema(description = "HDCAD - the offender's actual home detention curfew date.", example = "2020-02-03")
  private val homeDetentionCurfewActualDate: LocalDate? = null

  @Schema(description = "APD - the offender's actual parole date.", example = "2020-02-03")
  private val actualParoleDate: LocalDate? = null

  @Schema(
    description = "ROTL - the date on which offender will be released on temporary licence.",
    example = "2020-02-03",
  )
  private val releaseOnTemporaryLicenceDate: LocalDate? = null

  @Schema(
    description = "ERSED - the date on which offender will be eligible for early removal (under the Early Removal Scheme for foreign nationals).",
    example = "2020-02-03",
  )
  private val earlyRemovalSchemeEligibilityDate: LocalDate? = null

  @Schema(description = "ETD - early term date for offender.", example = "2020-02-03")
  private val earlyTermDate: LocalDate? = null

  @Schema(description = "MTD - mid term date for offender.", example = "2020-02-03")
  private val midTermDate: LocalDate? = null

  @Schema(description = "LTD - late term date for offender.", example = "2020-02-03")
  private val lateTermDate: LocalDate? = null

  @Schema(description = "TUSED - top-up supervision expiry date for offender.", example = "2020-02-03")
  private val topupSupervisionExpiryDate: LocalDate? = null

  @Schema(
    description = "Date on which minimum term is reached for parole (indeterminate/life sentences).",
    example = "2020-02-03",
  )
  private val tariffDate: LocalDate? = null

  @Schema(description = "DPRRD - Detention training order post recall release date", example = "2020-02-03")
  private val dtoPostRecallReleaseDate: LocalDate? = null

  @Schema(description = "TERSED - Tariff early removal scheme eligibility date", example = "2020-02-03")
  private val tariffEarlyRemovalSchemeEligibilityDate: LocalDate? = null

  @Schema(description = "Effective sentence end date", example = "2020-02-03")
  private val effectiveSentenceEndDate: LocalDate? = null
}
