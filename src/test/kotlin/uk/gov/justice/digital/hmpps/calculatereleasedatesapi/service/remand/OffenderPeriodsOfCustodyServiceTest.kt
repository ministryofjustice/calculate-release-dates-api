package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.remand

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import java.time.LocalDate
import java.time.LocalDateTime

class OffenderPeriodsOfCustodyServiceTest {
  private val prisonService = mock<PrisonService>()
  private val offenderPeriodsOfCustodyService = OffenderPeriodsOfCustodyService(prisonService)

  @Test
  fun `should pair movements chronologically and ignore unmatched movements`() {
    val prisonerId = "A1234AA"
    val admissionDate = LocalDate.of(2026, 1, 2)
    val releaseDate = LocalDate.of(2026, 1, 5)

    whenever(prisonService.getExternalMovements(prisonerId)).thenReturn(
      listOf(
        movement(prisonerId, LocalDate.of(2026, 1, 1), ExternalMovementDirection.OUT, "unmatched release"),
        movement(prisonerId, admissionDate, ExternalMovementDirection.IN, "remand"),
        movement(prisonerId, LocalDate.of(2026, 1, 3), ExternalMovementDirection.IN, "duplicate admission"),
        movement(prisonerId, releaseDate, ExternalMovementDirection.OUT, "release"),
        movement(prisonerId, LocalDate.of(2026, 1, 6), ExternalMovementDirection.OUT, "unmatched release"),
        movement(prisonerId, LocalDate.of(2026, 1, 7), ExternalMovementDirection.IN, "open period"),
      ),
    )
    whenever(prisonService.getCalculationsForAPrisonerId(eq(prisonerId))).thenReturn(emptyList())

    val periods = offenderPeriodsOfCustodyService.offenderRemandPeriods(prisonerId)

    assertThat(periods).hasSize(1)
    assertThat(periods.first().startDate).isEqualTo(admissionDate)
    assertThat(periods.first().endDate).isEqualTo(releaseDate)
    assertThat(periods.first().reason).isEqualTo("remand")

    verify(prisonService).getExternalMovements(eq(prisonerId))
    verify(prisonService).getCalculationsForAPrisonerId(eq(prisonerId))
    verifyNoMoreInteractions(prisonService)
  }

  @Test
  fun `should build multiple periods from unsorted movements`() {
    val prisonerId = "A1234AA"
    val firstAdmission = LocalDate.of(2026, 1, 2)
    val firstRelease = LocalDate.of(2026, 1, 4)
    val secondAdmission = LocalDate.of(2026, 2, 10)
    val secondRelease = LocalDate.of(2026, 2, 12)
    val firstCalculation = sentenceCalculationSummary(prisonerId, LocalDateTime.of(2026, 1, 3, 10, 0))
    val secondCalculation = sentenceCalculationSummary(prisonerId, LocalDateTime.of(2026, 2, 11, 10, 0))

    whenever(prisonService.getExternalMovements(prisonerId)).thenReturn(
      listOf(
        movement(prisonerId, secondRelease, ExternalMovementDirection.OUT, "release"),
        movement(prisonerId, secondAdmission, ExternalMovementDirection.IN, "return from remand"),
        movement(prisonerId, firstRelease, ExternalMovementDirection.OUT, "release"),
        movement(prisonerId, firstAdmission, ExternalMovementDirection.IN, "first remand"),
      ),
    )
    whenever(prisonService.getCalculationsForAPrisonerId(eq(prisonerId))).thenReturn(listOf(firstCalculation, secondCalculation))

    val periods = offenderPeriodsOfCustodyService.offenderRemandPeriods(prisonerId)

    assertThat(periods).hasSize(2)
    assertThat(periods[0].startDate).isEqualTo(firstAdmission)
    assertThat(periods[0].endDate).isEqualTo(firstRelease)
    assertThat(periods[0].reason).isEqualTo("first remand")
    assertThat(periods[0].calculations).containsExactly(firstCalculation)

    assertThat(periods[1].startDate).isEqualTo(secondAdmission)
    assertThat(periods[1].endDate).isEqualTo(secondRelease)
    assertThat(periods[1].reason).isEqualTo("return from remand")
    assertThat(periods[1].calculations).containsExactly(secondCalculation)

    verify(prisonService).getExternalMovements(eq(prisonerId))
    verify(prisonService).getCalculationsForAPrisonerId(eq(prisonerId))
    verifyNoMoreInteractions(prisonService)
  }

  private fun sentenceCalculationSummary(prisonerId: String, calculationDate: LocalDateTime): SentenceCalculationSummary = SentenceCalculationSummary(
    bookingId = 1L,
    offenderNo = prisonerId,
    firstName = "First",
    lastName = "Last",
    agencyLocationId = "MDI",
    agencyDescription = "Moorland (HMP)",
    offenderSentCalculationId = 1L,
    calculationDate = calculationDate,
    staffId = 123L,
    commentText = "comment",
    calculationReason = "reason",
    calculatedByUserId = "USER",
    calculatedByFirstName = "Test",
    calculatedByLastName = "User",
  )

  private fun movement(
    prisonerId: String,
    movementDate: LocalDate?,
    direction: ExternalMovementDirection,
    movementReason: String?,
  ) = PrisonApiExternalMovement(
    offenderNo = prisonerId,
    createDateTime = null,
    movementType = "REL",
    movementTypeDescription = null,
    directionCode = direction.name,
    movementDate = movementDate,
    movementTime = null,
    movementReason = movementReason,
    movementReasonCode = null,
    fromAgency = null,
    commentText = null,
  )
}
