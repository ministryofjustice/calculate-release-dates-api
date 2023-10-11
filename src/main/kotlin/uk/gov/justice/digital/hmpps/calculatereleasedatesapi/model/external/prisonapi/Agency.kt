package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class Agency(
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Agency identifier.", example = "MDI")
  private var agencyId: String? = null,

  @Schema(
    requiredMode = Schema.RequiredMode.REQUIRED,
    description = "Agency description.",
    example = "Moorland (HMP & YOI)",
  )
  var description: String? = null,

  @Schema(description = "Long description of the agency", example = "Moorland (HMP & YOI)")
  var longDescription: String? = null,

  @Schema(
    requiredMode = Schema.RequiredMode.REQUIRED,
    description = "Agency type.  Reference domain is AGY_LOC_TYPE",
    example = "INST",
    allowableValues = ["CRC", "POLSTN", "INST", "COMM", "APPR", "CRT", "POLICE", "IMDC", "TRN", "OUT", "YOT", "SCH", "STC", "HOST", "AIRPORT", "HSHOSP", "HOSPITAL", "PECS", "PAR", "PNP", "PSY"],
  )
  var agencyType: String? = null,

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Indicates the Agency is active", example = "true")
  var active: Boolean = true,

  @Schema(
    description = "Court Type.  Reference domain is JURISDICTION",
    example = "CC",
    allowableValues = ["CACD", "CB", "CC", "CO", "DCM", "GCM", "IMM", "MC", "OTHER", "YC"],
  )
  var courtType: String? = null,

  @Schema(description = "Date agency became inactive", example = "2012-01-12")
  var deactivationDate: LocalDate? = null,

  @Schema(description = "List of addresses associated with agency")
  var addresses: List<AddressDto>? = null,

  @Schema(description = "List of phones associated with agency")
  var phones: List<Telephone>? = null,

  @Schema(description = "List of emails associated with agency")
  var emails: List<Email>? = null,

)
