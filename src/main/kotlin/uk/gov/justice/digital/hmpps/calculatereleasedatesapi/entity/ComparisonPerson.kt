package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

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

@Entity
@Table
class ComparisonPerson(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  val comparisonId: Long,

  @NotNull
  val person: String,

  @NotNull
  val latestBookingId: Long,

  var isMatch: Boolean? = null,

  var isValid: Boolean? = null,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val validationMessages: JsonNode? = null,

)
