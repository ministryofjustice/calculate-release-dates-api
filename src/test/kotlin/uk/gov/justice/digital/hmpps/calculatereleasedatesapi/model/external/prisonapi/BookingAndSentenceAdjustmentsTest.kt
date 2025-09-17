package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import java.time.LocalDate

class BookingAndSentenceAdjustmentsTest {

  private val prisonerDetails = PrisonerDetails(offenderNo = "A1234BC", bookingId = 1, dateOfBirth = LocalDate.of(1982, 2, 3))

  @ParameterizedTest
  @EnumSource(SentenceAdjustmentType::class)
  fun `can upgrade all sentence adjustment types`(type: SentenceAdjustmentType) {
    val original = SentenceAdjustment(
      sentenceSequence = 1,
      active = true,
      fromDate = LocalDate.now(),
      toDate = LocalDate.now().plusDays(1),
      numberOfDays = 1,
      type = type,
    )
    val dtos = BookingAndSentenceAdjustments(emptyList(), listOf(original)).upgrade(prisonerDetails)
    assertThat(dtos).hasSize(1)
    val upgraded = dtos.first()
    assertThat(upgraded.sentenceSequence).isEqualTo(original.sentenceSequence)
    assertThat(upgraded.fromDate).isEqualTo(original.fromDate)
    assertThat(upgraded.toDate).isEqualTo(original.toDate)
    assertThat(upgraded.days).isEqualTo(original.numberOfDays)
    assertThat(upgraded.status).isEqualTo(AdjustmentDto.Status.ACTIVE)
  }

  @ParameterizedTest
  @EnumSource(BookingAdjustmentType::class)
  fun `can upgrade all booking adjustment types`(type: BookingAdjustmentType) {
    val original = BookingAdjustment(
      active = true,
      fromDate = LocalDate.now(),
      toDate = LocalDate.now().plusDays(1),
      numberOfDays = 1,
      type = type,
    )
    val dtos = BookingAndSentenceAdjustments(listOf(original), emptyList()).upgrade(prisonerDetails)
    assertThat(dtos).hasSize(1)
    val upgraded = dtos.first()
    assertThat(upgraded.fromDate).isEqualTo(original.fromDate)
    assertThat(upgraded.toDate).isEqualTo(original.toDate)
    assertThat(upgraded.days).isEqualTo(original.numberOfDays)
    assertThat(upgraded.status).isEqualTo(AdjustmentDto.Status.ACTIVE)
  }
}
