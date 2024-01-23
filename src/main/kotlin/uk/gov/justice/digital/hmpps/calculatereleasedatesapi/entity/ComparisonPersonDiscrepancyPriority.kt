package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyPriority

@Entity
@Table
class ComparisonPersonDiscrepancyPriority(
  @Id
  val id: Int = -1,

  @NotNull
  @Enumerated(EnumType.STRING)
  val priority: DiscrepancyPriority,
) {
  constructor(priority: DiscrepancyPriority) : this(priority.ordinal, priority)
}
