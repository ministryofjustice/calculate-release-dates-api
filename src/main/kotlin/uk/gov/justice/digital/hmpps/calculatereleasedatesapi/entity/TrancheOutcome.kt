package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.jetbrains.annotations.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import java.time.LocalDate

@Entity
@Table
data class TrancheOutcome(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  @OneToOne(optional = false)
  @JoinColumn(name = "calculationRequestId", nullable = false, updatable = false)
  var calculationRequest: CalculationRequest = CalculationRequest(),

  @NotNull
  @Enumerated(value = EnumType.STRING)
  val allocatedTranche: SDSEarlyReleaseTranche,

  @NotNull
  @Enumerated(value = EnumType.STRING)
  val tranche: SDSEarlyReleaseTranche,

  @NotNull
  val affectedBySds40: Boolean = false,

  val outcomeDate: LocalDate? = LocalDate.now(),
)
