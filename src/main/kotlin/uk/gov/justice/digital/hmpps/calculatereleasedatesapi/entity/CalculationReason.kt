package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull

@Entity
@Table
data class CalculationReason(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,
  @NotNull
  @JsonIgnore
  val isActive: Boolean,
  @NotNull
  val isOther: Boolean,
  val displayName: String,
  @NotNull
  @JsonIgnore
  val isBulk: Boolean
)
