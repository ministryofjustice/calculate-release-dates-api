package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType

data class BookingAndSentenceAdjustments(
  val bookingAdjustments: List<BookingAdjustment>,
  val sentenceAdjustments: List<SentenceAdjustment>,
) {

  fun upgrade(prisoner: PrisonerDetails): List<AdjustmentDto> = bookingAdjustments.map {
    AdjustmentDto(
      bookingId = prisoner.bookingId,
      adjustmentType = fromBookingAdjustmentType(it.type),
      person = prisoner.offenderNo,
      fromDate = it.fromDate,
      toDate = it.toDate,
      days = it.numberOfDays,
      effectiveDays = it.numberOfDays,
      status = if (it.active) AdjustmentDto.Status.ACTIVE else AdjustmentDto.Status.INACTIVE,
    )
  } + sentenceAdjustments.map {
    AdjustmentDto(
      bookingId = prisoner.bookingId,
      adjustmentType = fromSentenceAdjustmentType(it.type),
      person = prisoner.offenderNo,
      fromDate = it.fromDate,
      toDate = it.toDate,
      days = it.numberOfDays,
      effectiveDays = it.numberOfDays,
      status = if (it.active) AdjustmentDto.Status.ACTIVE else AdjustmentDto.Status.INACTIVE,
      sentenceSequence = it.sentenceSequence,
    )
  }

  companion object {
    private fun toSentenceAdjustmentType(type: AdjustmentDto.AdjustmentType): SentenceAdjustmentType = when (type) {
      AdjustmentDto.AdjustmentType.REMAND -> SentenceAdjustmentType.REMAND
      AdjustmentDto.AdjustmentType.TAGGED_BAIL -> SentenceAdjustmentType.TAGGED_BAIL
      AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS -> SentenceAdjustmentType.UNUSED_REMAND
      AdjustmentDto.AdjustmentType.CUSTODY_ABROAD -> SentenceAdjustmentType.TIME_SPENT_IN_CUSTODY_ABROAD
      AdjustmentDto.AdjustmentType.APPEAL_APPLICANT -> SentenceAdjustmentType.TIME_SPENT_AS_AN_APPEAL_APPLICANT
      else -> throw IllegalArgumentException("Unknown adjustment type $type")
    }

    private fun fromSentenceAdjustmentType(type: SentenceAdjustmentType): AdjustmentDto.AdjustmentType = when (type) {
      SentenceAdjustmentType.REMAND -> AdjustmentDto.AdjustmentType.REMAND
      SentenceAdjustmentType.RECALL_SENTENCE_REMAND -> AdjustmentDto.AdjustmentType.REMAND
      SentenceAdjustmentType.TAGGED_BAIL -> AdjustmentDto.AdjustmentType.TAGGED_BAIL
      SentenceAdjustmentType.RECALL_SENTENCE_TAGGED_BAIL -> AdjustmentDto.AdjustmentType.TAGGED_BAIL
      SentenceAdjustmentType.UNUSED_REMAND -> AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS
      SentenceAdjustmentType.TIME_SPENT_IN_CUSTODY_ABROAD -> AdjustmentDto.AdjustmentType.CUSTODY_ABROAD
      SentenceAdjustmentType.TIME_SPENT_AS_AN_APPEAL_APPLICANT -> AdjustmentDto.AdjustmentType.APPEAL_APPLICANT
    }

    private fun toBookingAdjustmentType(type: AdjustmentDto.AdjustmentType): BookingAdjustmentType = when (type) {
      AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE -> BookingAdjustmentType.UNLAWFULLY_AT_LARGE
      AdjustmentDto.AdjustmentType.LAWFULLY_AT_LARGE -> BookingAdjustmentType.LAWFULLY_AT_LARGE
      AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED -> BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED
      AdjustmentDto.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED -> BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED
      AdjustmentDto.AdjustmentType.SPECIAL_REMISSION -> BookingAdjustmentType.SPECIAL_REMISSION
      else -> throw IllegalArgumentException("Unknown adjustment type $type")
    }

    private fun fromBookingAdjustmentType(type: BookingAdjustmentType): AdjustmentDto.AdjustmentType = when (type) {
      BookingAdjustmentType.UNLAWFULLY_AT_LARGE -> AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE
      BookingAdjustmentType.LAWFULLY_AT_LARGE -> AdjustmentDto.AdjustmentType.LAWFULLY_AT_LARGE
      BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED -> AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED
      BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED -> AdjustmentDto.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
      BookingAdjustmentType.SPECIAL_REMISSION -> AdjustmentDto.AdjustmentType.SPECIAL_REMISSION
    }

    fun downgrade(adjustments: List<AdjustmentDto>): BookingAndSentenceAdjustments = BookingAndSentenceAdjustments(
      sentenceAdjustments = adjustments.filter { SENTENCE_ADJUSTMENT_TYPES.contains(it.adjustmentType) }.map {
        SentenceAdjustment(
          sentenceSequence = it.sentenceSequence!!,
          toDate = it.toDate,
          fromDate = it.fromDate,
          active = it.status == AdjustmentDto.Status.ACTIVE,
          numberOfDays = it.effectiveDays!!,
          type = toSentenceAdjustmentType(it.adjustmentType),
        )
      },
      bookingAdjustments = adjustments.filterNot { SENTENCE_ADJUSTMENT_TYPES.contains(it.adjustmentType) }.map {
        BookingAdjustment(
          toDate = it.toDate,
          fromDate = it.fromDate!!,
          active = it.status == AdjustmentDto.Status.ACTIVE,
          numberOfDays = it.effectiveDays!!,
          type = toBookingAdjustmentType(it.adjustmentType),

        )
      },
    )

    private val SENTENCE_ADJUSTMENT_TYPES = listOf(AdjustmentDto.AdjustmentType.REMAND, AdjustmentDto.AdjustmentType.TAGGED_BAIL, AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS, AdjustmentDto.AdjustmentType.APPEAL_APPLICANT, AdjustmentDto.AdjustmentType.CUSTODY_ABROAD)
  }
}
