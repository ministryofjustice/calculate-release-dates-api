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

/*
-- Create new table to store a comparison
CREATE TABLE comparison
(
id                          serial                        PRIMARY KEY,
comparison_reference        UUID                          NOT NULL,
comparison_short_reference  varchar(8)                    NOT NULL,
criteria                    JSONB                         NOT NULL,
calculated_at               timestamp with time zone      NOT NULL,
calculated_by_username      varchar(20)                   NOT NULL,
manual_input                boolean                       NOT NULL,
prison                      varchar(5),
); */
