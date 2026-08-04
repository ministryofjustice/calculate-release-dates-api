package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.remand

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing.UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.HistoricCalculationsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RemandAndSentencingService
import java.time.LocalDate
import java.time.LocalDateTime

class OffenderPeriodsOfCustodyServiceTest {
  private val prisonService = mock<PrisonService>()
  private val historicCalculationsService = mock<HistoricCalculationsService>()
  private val remandAndSentencingService = mock<RemandAndSentencingService>()
  private val offenderPeriodsOfCustodyService = OffenderPeriodsOfCustodyService(prisonService, historicCalculationsService, remandAndSentencingService)

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
    whenever(historicCalculationsService.getHistoricCalculationsForPrisoner(eq(prisonerId))).thenReturn(emptyList())
    whenever(remandAndSentencingService.getRecallsForOffender(eq(prisonerId))).thenReturn(emptyList())

    val periods = offenderPeriodsOfCustodyService.offenderRemandPeriods(prisonerId)

    assertThat(periods.size).isEqualTo(1)
    assertThat(periods.first().startDate).isEqualTo(admissionDate)
    assertThat(periods.first().endDate).isEqualTo(releaseDate)
    assertThat(periods.first().reason).isEqualTo("remand")
    assertThat(periods.first().recalls).isEmpty()

    verify(prisonService).getExternalMovements(eq(prisonerId))
    verify(historicCalculationsService).getHistoricCalculationsForPrisoner(eq(prisonerId))
    verify(remandAndSentencingService).getRecallsForOffender(eq(prisonerId))
  }

  @Test
  fun `should build multiple periods from unsorted movements`() {
    val prisonerId = "A1234AA"
    val firstAdmission = LocalDate.of(2026, 1, 2)
    val firstRelease = LocalDate.of(2026, 1, 4)
    val secondAdmission = LocalDate.of(2026, 2, 10)
    val secondRelease = LocalDate.of(2026, 2, 12)
    val firstCalculation = historicCalculation(prisonerId, LocalDateTime.of(2026, 1, 3, 10, 0))
    val secondCalculation = historicCalculation(prisonerId, LocalDateTime.of(2026, 2, 11, 10, 0))

    whenever(prisonService.getExternalMovements(prisonerId)).thenReturn(
      listOf(
        movement(prisonerId, secondRelease, ExternalMovementDirection.OUT, "release"),
        movement(prisonerId, secondAdmission, ExternalMovementDirection.IN, "return from remand"),
        movement(prisonerId, firstRelease, ExternalMovementDirection.OUT, "release"),
        movement(prisonerId, firstAdmission, ExternalMovementDirection.IN, "first remand"),
      ),
    )
    whenever(historicCalculationsService.getHistoricCalculationsForPrisoner(eq(prisonerId))).thenReturn(listOf(firstCalculation, secondCalculation))
    whenever(remandAndSentencingService.getRecallsForOffender(eq(prisonerId))).thenReturn(emptyList())

    val periods = offenderPeriodsOfCustodyService.offenderRemandPeriods(prisonerId)

    assertThat(periods.size).isEqualTo(2)
    assertThat(periods[0].startDate).isEqualTo(firstAdmission)
    assertThat(periods[0].endDate).isEqualTo(firstRelease)
    assertThat(periods[0].reason).isEqualTo("first remand")
    assertThat(periods[0].calculations).containsExactly(firstCalculation)
    assertThat(periods[0].recalls).isEmpty()

    assertThat(periods[1].startDate).isEqualTo(secondAdmission)
    assertThat(periods[1].endDate).isEqualTo(secondRelease)
    assertThat(periods[1].reason).isEqualTo("return from remand")
    assertThat(periods[1].calculations).containsExactly(secondCalculation)
    assertThat(periods[1].recalls).isEmpty()

    verify(prisonService).getExternalMovements(eq(prisonerId))
    verify(historicCalculationsService).getHistoricCalculationsForPrisoner(eq(prisonerId))
    verify(remandAndSentencingService).getRecallsForOffender(eq(prisonerId))
  }

  @Test
  fun `should assign recalls to the correct custody period`() {
    val prisonerId = "A1234AA"
    val firstAdmission = LocalDate.of(2026, 1, 2)
    val firstRelease = LocalDate.of(2026, 1, 31)
    val secondAdmission = LocalDate.of(2026, 3, 1)
    val secondRelease = LocalDate.of(2026, 3, 31)
    val recallInFirstPeriod = recall(prisonerId, LocalDateTime.of(2026, 1, 15, 12, 0))
    val recallInSecondPeriod = recall(prisonerId, LocalDateTime.of(2026, 3, 10, 12, 0))

    whenever(prisonService.getExternalMovements(prisonerId)).thenReturn(
      listOf(
        movement(prisonerId, firstAdmission, ExternalMovementDirection.IN, "remand"),
        movement(prisonerId, firstRelease, ExternalMovementDirection.OUT, "release"),
        movement(prisonerId, secondAdmission, ExternalMovementDirection.IN, "recall"),
        movement(prisonerId, secondRelease, ExternalMovementDirection.OUT, "release"),
      ),
    )
    whenever(historicCalculationsService.getHistoricCalculationsForPrisoner(eq(prisonerId))).thenReturn(emptyList())
    whenever(remandAndSentencingService.getRecallsForOffender(eq(prisonerId))).thenReturn(listOf(recallInFirstPeriod, recallInSecondPeriod))

    val periods = offenderPeriodsOfCustodyService.offenderRemandPeriods(prisonerId)

    assertThat(periods.size).isEqualTo(2)
    assertThat(periods[0].recalls).containsExactly(recallInFirstPeriod)
    assertThat(periods[1].recalls).containsExactly(recallInSecondPeriod)
  }

  private fun historicCalculation(prisonerId: String, calculationDate: LocalDateTime): HistoricCalculation = HistoricCalculation(
    offenderNo = prisonerId,
    calculationDate = calculationDate,
    calculationSource = CalculationSource.NOMIS,
    calculationViewConfiguration = null,
    commentText = null,
    calculationType = null,
    establishment = null,
    calculationRequestId = null,
    calculationReason = null,
    offenderSentCalculationId = null,
    genuineOverrideReasonCode = null,
    genuineOverrideReasonDescription = null,
    calculatedByUsername = "USER",
    calculatedByDisplayName = "Test User",
    secondCheckDetails = emptyList(),
  )

  private fun recall(prisonerId: String, createdAt: LocalDateTime): Recall = Recall(
    recallUuid = "recall-uuid-${createdAt.toLocalDate()}",
    prisonerId = prisonerId,
    revocationDate = createdAt.toLocalDate().toString(),
    returnToCustodyDate = createdAt.toLocalDate().toString(),
    inPrisonOnRevocationDate = false,
    recallType = "STANDARD_RECALL",
    createdAt = createdAt,
    createdByUsername = "USER",
    createdByPrison = "MDI",
    source = "NOMIS",
    courtCases = emptyList(),
    ual = UAL(id = "ual-1", days = 0),
    calculationRequestId = 0,
    isManual = false,
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
