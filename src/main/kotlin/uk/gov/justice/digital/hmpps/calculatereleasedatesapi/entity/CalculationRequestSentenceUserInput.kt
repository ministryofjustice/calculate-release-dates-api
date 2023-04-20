package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.validation.constraints.NotNull

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
