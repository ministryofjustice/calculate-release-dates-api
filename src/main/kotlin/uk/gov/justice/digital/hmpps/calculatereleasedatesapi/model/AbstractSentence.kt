package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.util.UUID

/**
 * A sentence that was imposed. This could be any sentence type and could be a single sentence as part of a consecutive
 * chain.
 */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type",
  defaultImpl = StandardDeterminateSentence::class
)
@JsonSubTypes(
  JsonSubTypes.Type(value = StandardDeterminateSentence::class, name = "StandardSentence"),
  JsonSubTypes.Type(value = ExtendedDeterminateSentence::class, name = "ExtendedDeterminateSentence")
)
abstract class AbstractSentence(
  override val offence: Offence,
  override val sentencedAt: LocalDate,
  open val identifier: UUID = UUID.randomUUID(),
  // Sentence UUIDS that this sentence is consecutive to.
  open val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  open val caseSequence: Int? = null,
  open val lineSequence: Int? = null,
  open val caseReference: String? = null,
  override val recallType: RecallType? = null
) : CalculableSentence {
  @JsonIgnore
  @Transient
  override lateinit var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  @Transient
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  @Transient
  override lateinit var releaseDateTypes: List<ReleaseDateType>
}
