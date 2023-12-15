package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.Formula
import org.hibernate.annotations.Type
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table
class Comparison(
  @JsonIgnore
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @JsonIgnore
  @NotNull
  val comparisonReference: UUID = UUID.randomUUID(),
  val comparisonShortReference: String = comparisonReference.toString().substring(0, 8),

  @NotNull
  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val criteria: JsonNode,

  @Column(length = 5)
  val prison: String? = null,

  @NotNull
  @Enumerated(EnumType.STRING)
  var comparisonType: ComparisonType,

  @NotNull
  val calculatedAt: LocalDateTime = LocalDateTime.now(),

  @NotNull
  @Column(length = 40)
  val calculatedByUsername: String,

  @NotNull
  @ManyToOne(cascade = [CascadeType.ALL])
  var comparisonStatus: ComparisonStatus,

  var numberOfPeopleCompared: Long = 0,

  @Formula("(SELECT count(*) FROM comparison_person cp WHERE cp.comparison_id=id and cp.is_match = false)")
  val numberOfMismatches: Long = 0,
) {
  override fun toString(): String {
    return "Comparison(id=$id, comparisonReference=$comparisonReference, comparisonShortReference='$comparisonShortReference', criteria=$criteria, prison=$prison, comparisonType=$comparisonType, calculatedAt=$calculatedAt, calculatedByUsername='$calculatedByUsername')"
  }
}
