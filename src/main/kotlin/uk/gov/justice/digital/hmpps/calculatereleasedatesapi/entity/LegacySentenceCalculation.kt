package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "legacy_sentence_calculations")
data class LegacySentenceCalculation(

  @Id
  @Column(nullable = false)
  @NotNull
  var nomisCalculationId: Long,

  @Column(nullable = false)
  @NotNull
  var prisonerId: String,

  @Column(nullable = false)
  @NotNull
  var bookingId: Long,

  @Column
  val crdsCalculationReference: UUID?,

  @Column
  val calculationRequestId: Long?,

  @Column(nullable = false)
  @NotNull
  var calculationDate: LocalDateTime,

  @Column
  val commentText: String?,

  @Column(nullable = false)
  @NotNull
  var calculationReasonCode: String,

  @Column
  val hdcedCalculatedDate: LocalDate?,

  @Column
  val hdcedOverriddenDate: LocalDate?,

  @Column
  val hdcadCalculatedDate: LocalDate?,

  @Column
  val hdcadOverriddenDate: LocalDate?,

  @Column
  val etdCalculatedDate: LocalDate?,

  @Column
  val etdOverriddenDate: LocalDate?,

  @Column
  val mtdCalculatedDate: LocalDate?,

  @Column
  val mtdOverriddenDate: LocalDate?,

  @Column
  val ltdCalculatedDate: LocalDate?,

  @Column
  val ltdOverriddenDate: LocalDate?,

  @Column
  val ardCalculatedDate: LocalDate?,

  @Column
  val ardOverriddenDate: LocalDate?,

  @Column
  val crdCalculatedDate: LocalDate?,

  @Column
  val crdOverriddenDate: LocalDate?,

  @Column
  val pedCalculatedDate: LocalDate?,

  @Column
  val pedOverriddenDate: LocalDate?,

  @Column
  val apdCalculatedDate: LocalDate?,

  @Column
  val apdOverriddenDate: LocalDate?,

  @Column
  val npdCalculatedDate: LocalDate?,

  @Column
  val npdOverriddenDate: LocalDate?,

  @Column
  val ledCalculatedDate: LocalDate?,

  @Column
  val ledOverriddenDate: LocalDate?,

  @Column
  val sedCalculatedDate: LocalDate?,

  @Column
  val sedOverriddenDate: LocalDate?,

  @Column
  val prrdCalculatedDate: LocalDate?,

  @Column
  val prrdOverriddenDate: LocalDate?,

  @Column
  val tariffCalculatedDate: LocalDate?,

  @Column
  val tariffOverriddenDate: LocalDate?,

  @Column
  val dprrdCalculatedDate: LocalDate?,

  @Column
  val dprrdOverriddenDate: LocalDate?,

  @Column
  val ersedOverriddenDate: LocalDate?,

  @Column
  val tersedOverriddenDate: LocalDate?,

  @Column
  val rotlOverriddenDate: LocalDate?,

  @Column
  val tusedCalculatedDate: LocalDate?,

  @Column
  val tusedOverriddenDate: LocalDate?,

  @Column(nullable = false)
  @NotNull
  var nomisCreatedAt: LocalDateTime,

  @Column(nullable = false)
  @NotNull
  var nomisCreatedBy: String,

  @Column
  val nomisModifiedAt: LocalDateTime?,

  @Column
  val nomisModifiedBy: String?,

  @Column
  val nomisRecordedAt: LocalDateTime?,

  @Column
  val nomisRecordedBy: String?,
) {

  fun toDto(): LegacySentenceCalculationDto = LegacySentenceCalculationDto(
    nomisCalculationId = this.nomisCalculationId,
    prisonerId = this.prisonerId,
    bookingId = this.bookingId,
    calculationDate = this.calculationDate,
    commentText = this.commentText,
    calculationReasonCode = this.calculationReasonCode,
    hdcedCalculatedDate = this.hdcedCalculatedDate,
    hdcedOverriddenDate = this.hdcedOverriddenDate,
    hdcadCalculatedDate = this.hdcadCalculatedDate,
    hdcadOverriddenDate = this.hdcadOverriddenDate,
    etdCalculatedDate = this.etdCalculatedDate,
    etdOverriddenDate = this.etdOverriddenDate,
    mtdCalculatedDate = this.mtdCalculatedDate,
    mtdOverriddenDate = this.mtdOverriddenDate,
    ltdCalculatedDate = this.ltdCalculatedDate,
    ltdOverriddenDate = this.ltdOverriddenDate,
    ardCalculatedDate = this.ardCalculatedDate,
    ardOverriddenDate = this.ardOverriddenDate,
    crdCalculatedDate = this.crdCalculatedDate,
    crdOverriddenDate = this.crdOverriddenDate,
    pedCalculatedDate = this.pedCalculatedDate,
    pedOverriddenDate = this.pedOverriddenDate,
    apdCalculatedDate = this.apdCalculatedDate,
    apdOverriddenDate = this.apdOverriddenDate,
    npdCalculatedDate = this.npdCalculatedDate,
    npdOverriddenDate = this.npdOverriddenDate,
    ledCalculatedDate = this.ledCalculatedDate,
    ledOverriddenDate = this.ledOverriddenDate,
    sedCalculatedDate = this.sedCalculatedDate,
    sedOverriddenDate = this.sedOverriddenDate,
    prrdCalculatedDate = this.prrdCalculatedDate,
    prrdOverriddenDate = this.prrdOverriddenDate,
    tariffCalculatedDate = this.tariffCalculatedDate,
    tariffOverriddenDate = this.tariffOverriddenDate,
    dprrdCalculatedDate = this.dprrdCalculatedDate,
    dprrdOverriddenDate = this.dprrdOverriddenDate,
    ersedOverriddenDate = this.ersedOverriddenDate,
    tersedOverriddenDate = this.tersedOverriddenDate,
    rotlOverriddenDate = this.rotlOverriddenDate,
    tusedCalculatedDate = this.tusedCalculatedDate,
    tusedOverriddenDate = this.tusedOverriddenDate,
    nomisCreatedAt = this.nomisCreatedAt,
    nomisCreatedBy = this.nomisCreatedBy,
    nomisModifiedAt = this.nomisModifiedAt,
    nomisModifiedBy = this.nomisModifiedBy,
    nomisRecordedAt = this.nomisRecordedAt,
    nomisRecordedBy = this.nomisRecordedBy,
  )

  companion object {
    fun fromDto(legacySentenceCalculation: LegacySentenceCalculationDto, calculationRequest: CalculationRequest?): LegacySentenceCalculation = LegacySentenceCalculation(
      nomisCalculationId = legacySentenceCalculation.nomisCalculationId,
      prisonerId = legacySentenceCalculation.prisonerId,
      bookingId = legacySentenceCalculation.bookingId,
      crdsCalculationReference = calculationRequest?.calculationReference,
      calculationRequestId = calculationRequest?.id,
      calculationDate = legacySentenceCalculation.calculationDate,
      commentText = legacySentenceCalculation.commentText,
      calculationReasonCode = legacySentenceCalculation.calculationReasonCode,
      hdcedCalculatedDate = legacySentenceCalculation.hdcedCalculatedDate,
      hdcedOverriddenDate = legacySentenceCalculation.hdcedOverriddenDate,
      hdcadCalculatedDate = legacySentenceCalculation.hdcadCalculatedDate,
      hdcadOverriddenDate = legacySentenceCalculation.hdcadOverriddenDate,
      etdCalculatedDate = legacySentenceCalculation.etdCalculatedDate,
      etdOverriddenDate = legacySentenceCalculation.etdOverriddenDate,
      mtdCalculatedDate = legacySentenceCalculation.mtdCalculatedDate,
      mtdOverriddenDate = legacySentenceCalculation.mtdOverriddenDate,
      ltdCalculatedDate = legacySentenceCalculation.ltdCalculatedDate,
      ltdOverriddenDate = legacySentenceCalculation.ltdOverriddenDate,
      ardCalculatedDate = legacySentenceCalculation.ardCalculatedDate,
      ardOverriddenDate = legacySentenceCalculation.ardOverriddenDate,
      crdCalculatedDate = legacySentenceCalculation.crdCalculatedDate,
      crdOverriddenDate = legacySentenceCalculation.crdOverriddenDate,
      pedCalculatedDate = legacySentenceCalculation.pedCalculatedDate,
      pedOverriddenDate = legacySentenceCalculation.pedOverriddenDate,
      apdCalculatedDate = legacySentenceCalculation.apdCalculatedDate,
      apdOverriddenDate = legacySentenceCalculation.apdOverriddenDate,
      npdCalculatedDate = legacySentenceCalculation.npdCalculatedDate,
      npdOverriddenDate = legacySentenceCalculation.npdOverriddenDate,
      ledCalculatedDate = legacySentenceCalculation.ledCalculatedDate,
      ledOverriddenDate = legacySentenceCalculation.ledOverriddenDate,
      sedCalculatedDate = legacySentenceCalculation.sedCalculatedDate,
      sedOverriddenDate = legacySentenceCalculation.sedOverriddenDate,
      prrdCalculatedDate = legacySentenceCalculation.prrdCalculatedDate,
      prrdOverriddenDate = legacySentenceCalculation.prrdOverriddenDate,
      tariffCalculatedDate = legacySentenceCalculation.tariffCalculatedDate,
      tariffOverriddenDate = legacySentenceCalculation.tariffOverriddenDate,
      dprrdCalculatedDate = legacySentenceCalculation.dprrdCalculatedDate,
      dprrdOverriddenDate = legacySentenceCalculation.dprrdOverriddenDate,
      ersedOverriddenDate = legacySentenceCalculation.ersedOverriddenDate,
      tersedOverriddenDate = legacySentenceCalculation.tersedOverriddenDate,
      rotlOverriddenDate = legacySentenceCalculation.rotlOverriddenDate,
      tusedCalculatedDate = legacySentenceCalculation.tusedCalculatedDate,
      tusedOverriddenDate = legacySentenceCalculation.tusedOverriddenDate,
      nomisCreatedAt = legacySentenceCalculation.nomisCreatedAt,
      nomisCreatedBy = legacySentenceCalculation.nomisCreatedBy,
      nomisModifiedAt = legacySentenceCalculation.nomisModifiedAt,
      nomisModifiedBy = legacySentenceCalculation.nomisModifiedBy,
      nomisRecordedAt = legacySentenceCalculation.nomisRecordedAt,
      nomisRecordedBy = legacySentenceCalculation.nomisRecordedBy,
    )
  }
}
