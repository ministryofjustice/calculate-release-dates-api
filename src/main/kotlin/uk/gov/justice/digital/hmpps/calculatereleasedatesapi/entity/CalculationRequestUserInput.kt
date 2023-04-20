package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.Table
import javax.validation.constraints.NotNull

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
