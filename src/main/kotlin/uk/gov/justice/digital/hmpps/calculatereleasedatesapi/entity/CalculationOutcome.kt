package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Entity
@Table
data class CalculationOutcome(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  val calculationRequestId: Long,

  val outcomeDate: LocalDate? = LocalDate.now(),

  @NotNull
  val calculationDateType: String = "",

  @OneToOne(mappedBy = "calculationOutcome", fetch = FetchType.LAZY, optional = true)
  val dominantHistoricCalculationOutcome: DominantHistoricCalculationOutcome? = null
)
