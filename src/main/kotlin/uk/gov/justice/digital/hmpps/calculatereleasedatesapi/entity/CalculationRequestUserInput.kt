package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull

@Entity
@Table
data class CalculationRequestUserInput(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  @ManyToOne(optional = false)
  @JoinColumn(name = "calculationRequestId", nullable = false, updatable = false)
  var calculationRequest: CalculationRequest = CalculationRequest(),

  @NotNull
  var calculateErsed: Boolean = false,

  @NotNull
  var useOffenceIndicators: Boolean = false,

  @OneToMany(mappedBy = "calculationRequestUserInput", cascade = [CascadeType.ALL])
  val calculationRequestSentenceUserInputs: List<CalculationRequestSentenceUserInput> = ArrayList(),

) {
  init {
    calculationRequestSentenceUserInputs.forEach { it.calculationRequestUserInput = this }
  }
}
