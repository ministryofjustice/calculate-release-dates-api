package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "Legacy sentence calculation imported from NOMIS")
data class LegacySentenceCalculationDto(

  @Schema(description = "Source-assigned NOMIS calculation identity.")
  val nomisCalculationId: Long,

  @Schema(description = "The prisoner identifier.")
  val prisonerId: String,

  @Schema(description = "The booking identifier.")
  val bookingId: Long,

  @Schema(description = "The date of the calculation.")
  val calculationDate: LocalDateTime,

  @Schema(description = "Free text field from NOMIS which may contain the calculation reference if it came from CRDS originally.")
  val commentText: String? = null,

  @Schema(description = "NOMIS reason code")
  val calculationReasonCode: String,

  @Schema(description = "HDCED either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val hdcedCalculatedDate: LocalDate? = null,

  @Schema(description = "HDCED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val hdcedOverriddenDate: LocalDate? = null,

  @Schema(description = "HDCAD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val hdcadCalculatedDate: LocalDate? = null,

  @Schema(description = "HDCAD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val hdcadOverriddenDate: LocalDate? = null,

  @Schema(description = "ETD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val etdCalculatedDate: LocalDate? = null,

  @Schema(description = "ETD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val etdOverriddenDate: LocalDate? = null,

  @Schema(description = "MTD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val mtdCalculatedDate: LocalDate? = null,

  @Schema(description = "MTD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val mtdOverriddenDate: LocalDate? = null,

  @Schema(description = "LTD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val ltdCalculatedDate: LocalDate? = null,

  @Schema(description = "LTD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val ltdOverriddenDate: LocalDate? = null,

  @Schema(description = "ARD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val ardCalculatedDate: LocalDate? = null,

  @Schema(description = "ARD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val ardOverriddenDate: LocalDate? = null,

  @Schema(description = "CRD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val crdCalculatedDate: LocalDate? = null,

  @Schema(description = "CRD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val crdOverriddenDate: LocalDate? = null,

  @Schema(description = "PED either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val pedCalculatedDate: LocalDate? = null,

  @Schema(description = "PED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val pedOverriddenDate: LocalDate? = null,

  @Schema(description = "APD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val apdCalculatedDate: LocalDate? = null,

  @Schema(description = "APD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val apdOverriddenDate: LocalDate? = null,

  @Schema(description = "NPD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val npdCalculatedDate: LocalDate? = null,

  @Schema(description = "NPD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val npdOverriddenDate: LocalDate? = null,

  @Schema(description = "LED either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val ledCalculatedDate: LocalDate? = null,

  @Schema(description = "LED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val ledOverriddenDate: LocalDate? = null,

  @Schema(description = "SED either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val sedCalculatedDate: LocalDate? = null,

  @Schema(description = "SED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val sedOverriddenDate: LocalDate? = null,

  @Schema(description = "PRRD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val prrdCalculatedDate: LocalDate? = null,

  @Schema(description = "PRRD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val prrdOverriddenDate: LocalDate? = null,

  @Schema(description = "TARIFF either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val tariffCalculatedDate: LocalDate? = null,

  @Schema(description = "TARIFF overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val tariffOverriddenDate: LocalDate? = null,

  @Schema(description = "DPRRD either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val dprrdCalculatedDate: LocalDate? = null,

  @Schema(description = "DPRRD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val dprrdOverriddenDate: LocalDate? = null,

  @Schema(description = "ERSED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val ersedOverriddenDate: LocalDate? = null,

  @Schema(description = "TERSED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val tersedOverriddenDate: LocalDate? = null,

  @Schema(description = "ROTL overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val rotlOverriddenDate: LocalDate? = null,

  @Schema(description = "TUSED either calculated by NOMIS or CRDS or manually entered in CRDS.")
  val tusedCalculatedDate: LocalDate? = null,

  @Schema(description = "TUSED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field.")
  val tusedOverriddenDate: LocalDate? = null,

  @Schema(description = "NOMIS audit field relating to modifications of this calculation. Records the created at time")
  val nomisCreatedAt: LocalDateTime,

  @Schema(description = "NOMIS audit field relating to modifications of this calculation. Records the created by user")
  val nomisCreatedBy: String,

  @Schema(description = "NOMIS audit field relating to modifications of this calculation. Records the modified at time")
  val nomisModifiedAt: LocalDateTime? = null,

  @Schema(description = "NOMIS audit field relating to modifications of this calculation. Records the modified by user")
  val nomisModifiedBy: String? = null,

  @Schema(description = "NOMIS audit field relating to modifications of this calculation. Records the recorded at time")
  val nomisRecordedAt: LocalDateTime? = null,

  @Schema(description = "NOMIS audit field relating to modifications of this calculation. Records the recorded by user")
  val nomisRecordedBy: String? = null,
)
