package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull

@Entity
@Table
data class CalculationRequestUserInput(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @NotNull
  @OneToOne(optional = false)
  @JoinColumn(name = "calculationRequestId", nullable = false, updatable = false)
  var calculationRequest: CalculationRequest = CalculationRequest(),

  @NotNull
  var calculateErsed: Boolean = false,

  @NotNull
  var useOffenceIndicators: Boolean = false,

  @NotNull
  @Column("use_previously_recorded_sled_if_found")
  var usePreviouslyRecordedSLEDIfFound: Boolean = false,

  @OneToMany(mappedBy = "calculationRequestUserInput", cascade = [CascadeType.ALL])
  val calculationRequestSentenceUserInputs: List<CalculationRequestSentenceUserInput> = ArrayList(),

) {
  init {
    calculationRequestSentenceUserInputs.forEach { it.calculationRequestUserInput = this }
  }
}
