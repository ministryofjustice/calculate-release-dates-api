package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.JsonType
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.NamedAttributeNode
import jakarta.persistence.NamedEntityGraph
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.hibernate.Hibernate
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Type
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table
@NamedEntityGraph(
  name = "CalculationRequest.detail",
  attributeNodes = [
    NamedAttributeNode("calculationOutcomes"),
    NamedAttributeNode("calculationRequestUserInput"),
    NamedAttributeNode("reasonForCalculation"),
    NamedAttributeNode("historicalTusedSource"),
    NamedAttributeNode("allocatedSDSTranche"),
  ],
)
data class CalculationRequest(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = -1,

  @Column(nullable = false)
  val calculationReference: UUID = UUID.randomUUID(),

  @Column(nullable = false)
  val prisonerId: String = "",

  @Column(nullable = false)
  val bookingId: Long = -1L,

  @Column(nullable = false)
  var calculationStatus: String = "",

  @Column(nullable = false)
  val calculatedAt: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  val calculatedByUsername: String = "",

  val prisonerLocation: String? = null,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val inputData: JsonNode = JacksonUtil.toJsonNode("{}"),

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val sentenceAndOffences: JsonNode? = null,
  val sentenceAndOffencesVersion: Int? = 3,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val prisonerDetails: JsonNode? = null,
  val prisonerDetailsVersion: Int? = 0,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val adjustments: JsonNode? = null,
  val adjustmentsVersion: Int? = 0,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val returnToCustodyDate: JsonNode? = null,
  val returnToCustodyDateVersion: Int? = 0,

  @Type(value = JsonType::class)
  @Column(columnDefinition = "jsonb")
  val offenderFinePayments: JsonNode? = null,
  val offenderFinePaymentsVersion: Int? = 0,

  val breakdownHtml: String? = null,

  @JoinColumn(name = "calculationRequestId")
  @OneToMany
  @BatchSize(size = 1000)
  val calculationOutcomes: MutableList<CalculationOutcome> = ArrayList(),

  @Column
  @Enumerated(value = EnumType.STRING)
  var calculationType: CalculationType = CalculationType.CALCULATED,

  @OneToOne(mappedBy = "calculationRequest", cascade = [CascadeType.ALL])
  val calculationRequestUserInput: CalculationRequestUserInput? = null,

  @JoinColumn(name = "calculationRequestId")
  @OneToMany
  @BatchSize(size = 1000)
  val approvedDatesSubmissions: MutableList<ApprovedDatesSubmission> = ArrayList(),

  @JoinColumn(name = "reasonForCalculation")
  @ManyToOne
  @param:NotNull
  val reasonForCalculation: CalculationReason? = null,

  val otherReasonForCalculation: String? = null,

  @Enumerated(value = EnumType.STRING)
  val historicalTusedSource: HistoricalTusedSource? = null,

  @OneToOne(mappedBy = "calculationRequest", cascade = [CascadeType.ALL])
  val allocatedSDSTranche: TrancheOutcome? = null,

  @OneToMany(mappedBy = "calculationRequest", cascade = [CascadeType.ALL])
  var manualCalculationReason: MutableList<CalculationRequestManualReason>? = null,

  val version: String = "1",
) {
  init {
    calculationRequestUserInput?.calculationRequest = this
  }

  @Override
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as CalculationRequest
    return id != -1L && id == other.id
  }

  @Override
  override fun hashCode(): Int = javaClass.hashCode()

  @Override
  override fun toString(): String = this::class.simpleName + "(calculationReference = $calculationReference )"

}
