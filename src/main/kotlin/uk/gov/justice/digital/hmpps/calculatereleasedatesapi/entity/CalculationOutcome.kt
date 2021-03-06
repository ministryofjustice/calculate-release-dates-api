package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table
import javax.validation.constraints.NotNull

@Entity
@Table
data class CalculationOutcome(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  val calculationRequestId: Long,

  val outcomeDate: LocalDate = LocalDate.now(),

  @NotNull
  val calculationDateType: String = "",
)
