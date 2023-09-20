package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Entity
@Table
data class GenuineOverride(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,
  val reason: String,
  @OneToOne
  val originalCalculationRequest: CalculationRequest,
  @OneToOne
  var savedCalculation: CalculationRequest? = null,
  val isOverridden: Boolean = false,
  @NotNull
  val savedAt: LocalDateTime = LocalDateTime.now(),
)
