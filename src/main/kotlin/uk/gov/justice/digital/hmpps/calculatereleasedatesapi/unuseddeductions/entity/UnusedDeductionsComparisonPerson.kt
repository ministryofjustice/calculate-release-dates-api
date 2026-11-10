package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "unused_deductions_comparison_person")
class UnusedDeductionsComparisonPerson(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  val comparisonId: Long,

  val person: String,

  val lastName: String? = null,

  val latestBookingId: Long? = null,

  val reference: UUID = UUID.randomUUID(),
  val shortReference: String = reference.toString().substring(0, 8),

  val calculatedAt: LocalDateTime = LocalDateTime.now(),

  @Enumerated(EnumType.STRING)
  val outcome: UnusedDeductionsComparisonOutcome,

  /** The total days of all ACTIVE UNUSED_DEDUCTIONS adjustments currently held for the person. */
  val existingUnusedDeductions: Long,

  /** The days output by UnusedDeductionsCalculationService, null if it could not be calculated. */
  val calculatedUnusedDeductions: Long? = null,

  /** The number of days of custody the deductions are applied against. */
  val custodialDays: Long? = null,

  /** The total number of remand and tagged bail days used in the calculation. */
  val totalDeductions: Long? = null,

  /** The adjustments supplied to the calculation, including their current days and effectiveDays. */
  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val adjustments: JsonNode,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val validationMessages: JsonNode,

  val fatalException: String? = null,

  val establishment: String? = null,
)

enum class UnusedDeductionsComparisonOutcome {
  MATCH,
  MISMATCH,
  VALIDATION_ERROR,
  FATAL_ERROR,
}
