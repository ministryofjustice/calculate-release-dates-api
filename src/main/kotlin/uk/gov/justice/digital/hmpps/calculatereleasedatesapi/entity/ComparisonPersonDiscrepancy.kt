package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
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
  @ManyToOne
  @JoinColumn(name = "comparisonPersonId")
  val comparisonPerson: ComparisonPerson,

  @NotNull
  @ManyToOne
  @JoinColumn(name = "impactId")
  val discrepancyImpact: ComparisonPersonDiscrepancyImpact,

  @NotNull
  @OneToMany(mappedBy = "discrepancy", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
  val causes: List<ComparisonPersonDiscrepancyCause> = emptyList(),

  @NotNull
  @ManyToOne(optional = false)
  @JoinColumn(name = "priorityId")
  val discrepancyPriority: ComparisonPersonDiscrepancyPriority,

  val detail: String?,

  val action: String,

  @NotNull
  val createdBy: String,

  @NotNull
  val createdAt: LocalDateTime = LocalDateTime.now(),

  var supersededById: Long? = null,
)
