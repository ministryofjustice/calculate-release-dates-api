package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table
data class BankHolidayCache(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  var data: JsonNode = JacksonUtil.toJsonNode("{}"),

  @NotNull
  var cachedAt: LocalDateTime = LocalDateTime.now(),
)
