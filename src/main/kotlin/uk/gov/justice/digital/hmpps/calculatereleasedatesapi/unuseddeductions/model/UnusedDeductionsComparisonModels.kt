package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonProgress
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "Request to run an unused deductions comparison. Provide prisonerIds for a manual list, otherwise a prison code or 'all'.")
data class UnusedDeductionsComparisonInput(
  @Schema(description = "A prison code, or 'all' for every prison . Ignored when prisonerIds are provided", example = "MDI")
  val prison: String? = null,
  @Schema(description = "A manual list of prisoner numbers to compare")
  val prisonerIds: List<String>? = null,
)

@Schema(description = "An adjustment supplied as an input to the unused deductions calculation")
data class UnusedDeductionsAdjustmentInput(
  val id: UUID?,
  val adjustmentType: String,
  val source: String?,
  val fromDate: LocalDate?,
  val toDate: LocalDate?,
  val days: Int?,
  val effectiveDays: Int?,
)

data class UnusedDeductionsComparisonSummary(
  val comparisonShortReference: String,
  val prison: String?,
  val comparisonType: UnusedDeductionsComparisonType,
  val comparisonStatus: ComparisonStatus,
  val calculatedAt: LocalDateTime,
  val calculatedByUsername: String,
  val numberOfMismatches: Long,
  val numberOfValidationErrors: Long,
  val numberOfPeopleExpected: Long,
  val numberOfPeopleCompared: Long,
  val numberOfPeopleComparisonFailedFor: Long,
  val comparisonProgress: ComparisonProgress = ComparisonProgress.from(comparisonStatus, numberOfPeopleCompared, numberOfPeopleExpected, calculatedAt),
)

data class UnusedDeductionsComparisonPersonSummary(
  val personShortReference: String,
  val person: String,
  val lastName: String?,
  val establishment: String?,
  val outcome: UnusedDeductionsComparisonOutcome,
  val existingUnusedDeductions: Long,
  val calculatedUnusedDeductions: Long?,
  val custodialDays: Long?,
  val totalDeductions: Long?,
)

data class UnusedDeductionsComparisonOverview(
  val comparison: UnusedDeductionsComparisonSummary,
  val criteria: Map<String, Any?>,
  val people: List<UnusedDeductionsComparisonPersonSummary>,
  val page: UnusedDeductionsPageInfo,
)

data class UnusedDeductionsPageInfo(
  val number: Int,
  val size: Int,
  val totalElements: Long,
  val totalPages: Int,
)

data class UnusedDeductionsComparisonPersonDetail(
  val comparison: UnusedDeductionsComparisonSummary,
  val person: UnusedDeductionsComparisonPersonSummary,
  val adjustments: List<UnusedDeductionsAdjustmentInput>,
  val validationMessages: List<ValidationMessage>,
  val fatalException: String?,
  val calculatedAt: LocalDateTime,
)

data class UnusedDeductionsComparisonPage(
  val content: List<UnusedDeductionsComparisonSummary>,
  val page: UnusedDeductionsPageInfo,
)
