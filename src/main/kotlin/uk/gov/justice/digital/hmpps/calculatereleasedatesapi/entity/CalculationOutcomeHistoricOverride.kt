package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.jetbrains.annotations.NotNull
import java.time.LocalDate

@Entity
@Table
data class CalculationOutcomeHistoricOverride(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  val calculationRequestId: Long,

  @NotNull
  val calculationOutcomeDate: LocalDate,

  @NotNull
  val historicCalculationOutcomeId: Long,

  @NotNull
  val historicCalculationOutcomeDate: LocalDate,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST])
  @NotNull
  @JoinColumn(name = "calculation_outcome_id", referencedColumnName = "id", nullable = false)
  val calculationOutcome: CalculationOutcome,
)
