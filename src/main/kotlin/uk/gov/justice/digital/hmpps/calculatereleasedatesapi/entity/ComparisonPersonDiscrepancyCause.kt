package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancySubCategory

@Entity
@Table
class ComparisonPersonDiscrepancyCause(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @ManyToOne
  @JoinColumn(name = "discrepancy_id", nullable = false)
  val discrepancy: ComparisonPersonDiscrepancy,

  @NotNull
  @Enumerated(EnumType.STRING)
  val category: DiscrepancyCategory,

  @NotNull
  @Enumerated(EnumType.STRING)
  val subCategory: DiscrepancySubCategory,

  val detail: String? = null,
)
