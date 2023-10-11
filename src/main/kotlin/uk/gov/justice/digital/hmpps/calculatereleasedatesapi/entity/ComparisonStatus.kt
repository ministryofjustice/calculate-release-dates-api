package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue

@Entity
@Table
class ComparisonStatus(

  @Id
  val id: Int = -1,

  @NotNull
  val name: String,
) {
  constructor(comparisonStatusValue: ComparisonStatusValue) : this(
    id = comparisonStatusValue.ordinal,
    name = comparisonStatusValue.name,
  )
}
