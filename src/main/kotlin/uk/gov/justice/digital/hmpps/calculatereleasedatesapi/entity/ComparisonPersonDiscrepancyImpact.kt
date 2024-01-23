package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyImpact

@Entity
@Table
class ComparisonPersonDiscrepancyImpact(
  @Id
  val id: Int = -1,

  @NotNull
  @Enumerated(EnumType.STRING)
  val impact: DiscrepancyImpact,
) {
  constructor(impact: DiscrepancyImpact) : this(impact.ordinal, impact)
}
