package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

@Entity
@Table
data class CalculationRequestManualReason(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @JoinColumn(name = "calculationRequestId")
  @ManyToOne
  val calculationRequest: CalculationRequest,

  @NotNull
  @Enumerated(EnumType.STRING)
  val code: ValidationCode,

  @NotNull
  val message: String,
)
