package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.Type
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
  val criteria: JsonNode? = null,

  val prison: String? = null,

  @NotNull
  var manualInput: Boolean,

  @NotNull
  val calculatedAt: LocalDateTime = LocalDateTime.now(),

  @NotNull
  val calculatedByUsername: String,
)
