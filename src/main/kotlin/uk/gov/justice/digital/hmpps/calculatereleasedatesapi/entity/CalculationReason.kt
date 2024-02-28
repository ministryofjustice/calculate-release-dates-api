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
  val isBulk: Boolean,
  @JsonIgnore
  val nomisReason: String?,
  /**
   * A shortened version (sub 40 character) of the displayName to allow the comment to be meaningful in NOMIS.
   */
  @NotNull
  @JsonIgnore
  val nomisComment: String?,
  /**
   * Determines the order of the active reasons on the page, repository sorts on this field ascending.
   */
  @NotNull
  @JsonIgnore
  val displayRank: Int?,
)
