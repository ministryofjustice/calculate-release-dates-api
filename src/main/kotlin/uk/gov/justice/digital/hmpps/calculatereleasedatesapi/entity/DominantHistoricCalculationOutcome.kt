package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table
data class DominantHistoricCalculationOutcome(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  val bookingId: Long = -1,

  val outcomeDate: LocalDate,

  @OneToOne
  @JoinColumn(name = "calculation_outcome_id", referencedColumnName = "id", nullable = true)
  val calculationOutcome: CalculationOutcome? = null
)