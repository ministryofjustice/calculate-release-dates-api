package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull

@Entity
@Table
class DiscrepancyCategory(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  val category: String,

  val subCategory: String,
)
