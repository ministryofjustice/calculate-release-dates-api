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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "unused_deductions_comparison")
class UnusedDeductionsComparison(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  val comparisonReference: UUID = UUID.randomUUID(),
  val comparisonShortReference: String = comparisonReference.toString().substring(0, 8),

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val criteria: JsonNode,

  @Column(length = 5)
  val prison: String? = null,

  @Enumerated(EnumType.STRING)
  val comparisonType: UnusedDeductionsComparisonType,

  val calculatedAt: LocalDateTime = LocalDateTime.now(),

  @Column(length = 40)
  val calculatedByUsername: String,

  @Enumerated(EnumType.STRING)
  var comparisonStatus: ComparisonStatus = ComparisonStatus.SETUP,

  var numberOfPeopleExpected: Long = 1,

  var numberOfPeopleCompared: Long = 0,

  var numberOfPeopleComparisonFailedFor: Long = 0,

  @Transient
  var numberOfMismatches: Long = 0,
) {
  fun id(): Long = id!!
}

enum class UnusedDeductionsComparisonType {
  /** A manually supplied list of prisoners */
  MANUAL,

  /** Every calculable prisoner in a single prison */
  PRISON,

  /** Every calculable prisoner in every prison in the user's caseload */
  ALL_PRISONS,
}
