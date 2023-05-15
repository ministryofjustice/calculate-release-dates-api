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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType

@Entity
@Table
data class CalculationRequestSentenceUserInput(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,
  @NotNull
  @ManyToOne(optional = false)
  @JoinColumn(name = "calculationRequestUserInputId", nullable = false, updatable = false)
  var calculationRequestUserInput: CalculationRequestUserInput = CalculationRequestUserInput(),
  @NotNull
  val sentenceSequence: Int,
  @NotNull
  val offenceCode: String,
  @NotNull
  @Enumerated(EnumType.STRING)
  var type: UserInputType,
  @NotNull
  var userChoice: Boolean,
  @NotNull
  var nomisMatches: Boolean,
)
