package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Key Dates")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class KeyDates(
  @Schema(description = "Sentence start date.", example = "2010-02-03", requiredMode = Schema.RequiredMode.REQUIRED)
  private val sentenceStartDate: LocalDate? = null,

  @Schema(description = "Effective sentence end date", example = "2020-02-03")
  private val effectiveSentenceEndDate: LocalDate? = null,

  @Schema(description = "ADA - days added to sentence term due to adjustments.", example = "5")
  private val additionalDaysAwarded: Int? = null,

  @Schema(
    description = "Release date for non-DTO sentence (if applicable). This will be based on one of ARD, CRD, NPD or PRRD.",
    example = "2020-04-01",
  )
  private val nonDtoReleaseDate: LocalDate? = null,

  @Schema(
    description = "Indicates which type of non-DTO release date is the effective release date. One of 'ARD', 'CRD', 'NPD' or 'PRRD'.",
    example = "CRD",
    requiredMode = Schema.RequiredMode.REQUIRED,
  )
  private val nonDtoReleaseDateType: NonDtoReleaseDateType? = null,

  @Schema(description = "Confirmed release date for offender.", example = "2020-04-20")
  private val confirmedReleaseDate: LocalDate? = null,

  @Schema(
    description = "Confirmed, actual, approved, provisional or calculated release date for offender, according to offender release date algorithm." +
      "<h3>Algorithm</h3><ul><li>If there is a confirmed release date, the offender release date is the confirmed release date.</li><li>If there is no confirmed release date for the offender, the offender release date is either the actual parole date or the home detention curfew actual date.</li><li>If there is no confirmed release date, actual parole date or home detention curfew actual date for the offender, the release date is the later of the nonDtoReleaseDate or midTermDate value (if either or both are present)</li></ul>",
    example = "2020-04-01",
  )
  private val releaseDate: LocalDate? = null,

  @Schema(description = "SED - date on which sentence expires.", example = "2020-02-03")
  private val sentenceExpiryDate: LocalDate? = null,

  @Schema(
    description = "ARD - calculated automatic (unconditional) release date for offender.",
    example = "2020-02-03",
  )
  private val automaticReleaseDate: LocalDate? = null,

  @Schema(description = "CRD - calculated conditional release date for offender.", example = "2020-02-03")
  private val conditionalReleaseDate: LocalDate? = null,

  @Schema(
    description = "NPD - calculated non-parole date for offender (relating to the 1991 act).",
    example = "2020-02-03",
  )
  private val nonParoleDate: LocalDate? = null,

  @Schema(description = "PRRD - calculated post-recall release date for offender.", example = "2020-02-03")
  private val postRecallReleaseDate: LocalDate? = null,

  @Schema(description = "LED - date on which offender licence expires.", example = "2020-02-03")
  private val licenceExpiryDate: LocalDate? = null,

  @Schema(
    description = "HDCED - date on which offender will be eligible for home detention curfew.",
    example = "2020-02-03",
  )
  private val homeDetentionCurfewEligibilityDate: LocalDate? = null,

  @Schema(description = "PED - date on which offender is eligible for parole.", example = "2020-02-03")
  private val paroleEligibilityDate: LocalDate? = null,

  @Schema(description = "HDCAD - the offender's actual home detention curfew date.", example = "2020-02-03")
  private val homeDetentionCurfewActualDate: LocalDate? = null,

  @Schema(description = "APD - the offender's actual parole date.", example = "2020-02-03")
  private val actualParoleDate: LocalDate? = null,

  @Schema(
    description = "ROTL - the date on which offender will be released on temporary licence.",
    example = "2020-02-03",
  )
  private val releaseOnTemporaryLicenceDate: LocalDate? = null,

  @Schema(
    description = "ERSED - the date on which offender will be eligible for early removal (under the Early Removal Scheme for foreign nationals).",
    example = "2020-02-03",
  )
  private val earlyRemovalSchemeEligibilityDate: LocalDate? = null,

  @Schema(description = "ETD - early term date for offender.", example = "2020-02-03")
  private val earlyTermDate: LocalDate? = null,

  @Schema(description = "MTD - mid term date for offender.", example = "2020-02-03")
  private val midTermDate: LocalDate? = null,

  @Schema(description = "LTD - late term date for offender.", example = "2020-02-03")
  private val lateTermDate: LocalDate? = null,

  @Schema(description = "TUSED - top-up supervision expiry date for offender.", example = "2020-02-03")
  private val topupSupervisionExpiryDate: LocalDate? = null,

  @Schema(
    description = "Date on which minimum term is reached for parole (indeterminate/life sentences).",
    example = "2020-02-03",
  )
  private val tariffDate: LocalDate? = null,

  @Schema(description = "DPRRD - Detention training order post recall release date", example = "2020-02-03")
  private val dtoPostRecallReleaseDate: LocalDate? = null,

  @Schema(description = "TERSED - Tariff early removal scheme eligibility date", example = "2020-02-03")
  private val tariffEarlyRemovalSchemeEligibilityDate: LocalDate? = null,

  @get:Schema(
    description = "Top-up supervision start date for offender - calculated as licence end date + 1 day or releaseDate if licence end date not set.",
    example = "2019-04-01",
  )
  val topupSupervisionStartDate: LocalDate?,

  @get:Schema(
    description = "Offender's home detention curfew end date - calculated as one day before the releaseDate.",
    example = "2019-04-01",
  )
  val homeDetentionCurfewEndDate: LocalDate?,

)
