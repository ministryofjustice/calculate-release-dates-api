package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Entity
@Table
data class CalculationRequestSecondCheck(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,
  @NotNull
  @JoinColumn(name = "calculationRequestId", nullable = false, updatable = false)
  val calculationRequestId: Long,
  @NotNull
  val prisonerId: String,
  @NotNull
  val checkedAt: LocalDateTime = LocalDateTime.now(),
  @NotNull
  val checkedByUsername: String,
) {
  fun id(): Long = id!!
}
