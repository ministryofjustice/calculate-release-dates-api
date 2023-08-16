package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Court case details")
@JsonInclude(
  JsonInclude.Include.NON_NULL,
)
data class CourtSentences(
  @Schema(description = "The case information number", example = "TD20177010")
  val caseInfoNumber: String? = null,

  @Schema(description = "The case identifier (internal)", example = "1")
  val id: Long? = null,

  @Schema(description = "The case sequence number for the offender", example = "1")
  val caseSeq: Int? = null,

  @Schema(description = "The begin date of the court hearings", example = "2019-12-01")
  val beginDate: LocalDate? = null,

  @Schema(description = "Court details")
  val court: Agency? = null,

  @Schema(description = "The case type", example = "Adult")
  val caseType: String? = null,

  @Schema(description = "The prefix of the case number")
  val caseInfoPrefix: String? = null,

  @Schema(description = "The case status", example = "ACTIVE", allowableValues = ["ACTIVE", "CLOSED", "INACTIVE"])
  val caseStatus: String? = null,

  @Schema(description = "Court sentences associated with the court case") val sentences: List<SentencesOffencesTerms>? = null,

  @Schema(description = "Issuing Court Details")
  val issuingCourt: Agency? = null,

  @Schema(description = "Issuing Court Date")
  val issuingCourtDate: LocalDate? = null,
)
