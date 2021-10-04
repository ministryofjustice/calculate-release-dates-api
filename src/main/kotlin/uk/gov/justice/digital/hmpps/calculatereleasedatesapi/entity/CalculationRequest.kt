package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import com.fasterxml.jackson.databind.JsonNode
import com.vladmihalcea.hibernate.type.json.JsonType
import com.vladmihalcea.hibernate.type.json.internal.JacksonUtil
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import java.time.LocalDateTime
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToMany
import javax.persistence.Table
import javax.validation.constraints.NotNull

@Entity
@Table
@TypeDefs(
  TypeDef(name = "json", typeClass = JsonType::class)
)
data class CalculationRequest(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  val calculationReference: UUID = UUID.randomUUID(),

  @NotNull
  val prisonerId: String = "",

  @NotNull
  val bookingId: Long = -1L,

  @NotNull
  val calculationStatus: String = "",

  @NotNull
  val calculatedAt: LocalDateTime = LocalDateTime.now(),

  @NotNull
  val calculatedByUsername: String = "",

  @NotNull
  @Type(type = "json")
  @Column(columnDefinition = "jsonb")
  val inputData: JsonNode = JacksonUtil.toJsonNode("{}"),

  @JoinColumn(name = "calculationRequestId")
  @OneToMany
  @Fetch(FetchMode.JOIN)
  val calculationOutcomes: List<CalculationOutcome> = ArrayList(),
)
