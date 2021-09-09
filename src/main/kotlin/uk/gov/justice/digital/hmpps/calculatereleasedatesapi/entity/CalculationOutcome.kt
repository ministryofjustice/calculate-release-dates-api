package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import java.time.LocalDateTime
import java.util.UUID
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table
@TypeDefs(
  TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
data class CalculationOutcome(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,
  val calculationReference: UUID = UUID.randomUUID(),
  val outcomeDate: LocalDateTime = LocalDateTime.now(),
  val calculationDateType: String = "",
)
