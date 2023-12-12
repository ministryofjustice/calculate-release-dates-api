package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.jetbrains.annotations.NotNull
import java.time.LocalDateTime

@Entity
@Table
class ComparisonPersonDiscrepancy(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  @ManyToOne(optional = false)
  @JoinColumn(name = "impactId")
  val impact: DiscrepancyImpact,

  @NotNull
  @ManyToOne(optional = false)
  @JoinColumn(name = "categoryId")
  val category: DiscrepancyCategory,

  val other: String?,

  val detail: String?,

  @NotNull
  @ManyToOne(optional = false)
  @JoinColumn(name = "priorityId")
  val priority: DiscrepancyPriority,

  val action: String?,

  @NotNull
  val createdAt: LocalDateTime = LocalDateTime.now(),

  @NotNull
  val createdBy: String,

  val supersededById: Long? = null,
)
