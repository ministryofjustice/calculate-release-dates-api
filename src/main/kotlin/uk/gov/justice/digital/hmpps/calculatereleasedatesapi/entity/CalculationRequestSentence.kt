package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier

@Entity
@Table
data class CalculationRequestSentence(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  val calculationRequestId: Long,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb", name = "input_sentence_data_json")
  val inputSentenceData: JsonNode,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb", name = "sentence_adjustments_json")
  val sentenceAdjustments: JsonNode,

  val impactsFinalReleaseDate: Boolean,

  @Enumerated(EnumType.STRING)
  val releaseMultiplier: ReleaseMultiplier,
)
