package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.jetbrains.annotations.NotNull
import java.time.LocalDate

@Entity
@Table
data class CalculationOutcomeHistoricSledOverride(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @NotNull
  val calculationRequestId: Long,

  @NotNull
  val calculationOutcomeDate: LocalDate,

  @NotNull
  val historicCalculationRequestId: Long,

  @NotNull
  val historicCalculationOutcomeDate: LocalDate,

)
