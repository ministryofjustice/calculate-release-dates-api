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
  private val caseInfoNumber: String? = null,

  @Schema(description = "The case identifier (internal)", example = "1")
  private val id: Long? = null,

  @Schema(description = "The case sequence number for the offender", example = "1")
  private val caseSeq: Int? = null,

  @Schema(description = "The begin date of the court hearings", example = "2019-12-01")
  private val beginDate: LocalDate? = null,

  @Schema(description = "Court details")
  private val court: Agency? = null,

  @Schema(description = "The case type", example = "Adult")
  private val caseType: String? = null,

  @Schema(description = "The prefix of the case number")
  private val caseInfoPrefix: String? = null,

  @Schema(description = "The case status", example = "ACTIVE", allowableValues = ["ACTIVE", "CLOSED", "INACTIVE"])
  private val caseStatus: String? = null,

  @Schema(description = "Court sentences associated with the court case")
  private val sentences: List<SentencesOffencesTerms>? = null,

  @Schema(description = "Issuing Court Details")
  private val issuingCourt: Agency? = null,

  @Schema(description = "Issuing Court Date")
  private val issuingCourtDate: LocalDate? = null,
)
