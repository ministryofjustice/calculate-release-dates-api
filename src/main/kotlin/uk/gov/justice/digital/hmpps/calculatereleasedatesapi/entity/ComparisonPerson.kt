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
import java.util.*

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

  var isMatch: Boolean,

  var isValid: Boolean,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val validationMessages: JsonNode,

  @JsonIgnore
  @NotNull
  val reference: UUID = UUID.randomUUID(),
  val shortReference: String = reference.toString().substring(0, 8),

  @NotNull
  val calculatedAt: LocalDateTime = LocalDateTime.now(),

  @NotNull
  @Column(length = 40)
  val calculatedByUsername: String,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  var nomisDates: JsonNode,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  var overrideDates: JsonNode,

  var calculationRequestId: Long? = null,

)
