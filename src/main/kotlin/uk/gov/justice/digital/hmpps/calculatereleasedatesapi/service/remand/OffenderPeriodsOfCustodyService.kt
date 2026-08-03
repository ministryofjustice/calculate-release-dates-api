package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.remand

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import java.time.LocalDate

data class OffenderCustodyPeriod(
  val startDate: LocalDate,
  val endDate: LocalDate,
  val reason: String,
  val courtCases: List<Unit>,
  val recalls: List<Unit>,
  val immigrationDetention: List<Unit>,
  val calculations: List<SentenceCalculationSummary>,
)

@Service
class OffenderPeriodsOfCustodyService(private val prisonService: PrisonService) {
  fun offenderRemandPeriods(prisonerId: String): List<OffenderCustodyPeriod> {
    val externalMovements = prisonService.getExternalMovements(prisonerId)
    val periods = buildCustodyPeriods(externalMovements)
    if (periods.isEmpty()) return emptyList()

    val nomisCalculations = prisonService.getCalculationsForAPrisonerId(prisonerId)

    return periods.map { period ->
      val calculations = nomisCalculations.filter {
        it.calculationDate.toLocalDate() in period.startDate..period.endDate
      }

      OffenderCustodyPeriod(
        startDate = period.startDate,
        endDate = period.endDate,
        reason = period.reason,
        courtCases = emptyList(),
        recalls = emptyList(),
        immigrationDetention = emptyList(),
        calculations = calculations,
      )
    }
  }

  private fun buildCustodyPeriods(externalMovements: List<PrisonApiExternalMovement>): List<CustodyPeriod> {
    val normalizedMovements = externalMovements
      .mapNotNull { movement -> normalizedMovement(movement) }
      .sortedBy { movement -> movement.movementDate }

    if (normalizedMovements.isEmpty()) return emptyList()

    val periods = mutableListOf<CustodyPeriod>()
    var currentAdmission: NormalizedMovement? = null

    normalizedMovements.forEach { movement ->
      when (movement.direction) {
        ExternalMovementDirection.IN -> {
          if (currentAdmission == null) {
            currentAdmission = movement
          }
        }

        ExternalMovementDirection.OUT -> {
          if (currentAdmission != null && !movement.movementDate.isBefore(currentAdmission.movementDate)) {
            periods.add(
              CustodyPeriod(
                startDate = currentAdmission.movementDate,
                endDate = movement.movementDate,
                reason = currentAdmission.movementReason,
              ),
            )
            currentAdmission = null
          }
        }
      }
    }

    return periods
  }

  private fun normalizedMovement(movement: PrisonApiExternalMovement): NormalizedMovement? {
    val movementDate = movement.movementDate ?: return null
    val movementReason = movement.movementReason ?: return null
    val direction = ExternalMovementDirection.entries.firstOrNull { it.name == movement.directionCode } ?: return null

    return NormalizedMovement(
      direction = direction,
      movementDate = movementDate,
      movementReason = movementReason,
    )
  }

  private data class NormalizedMovement(
    val direction: ExternalMovementDirection,
    val movementDate: LocalDate,
    val movementReason: String,
  )

  private data class CustodyPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String,
  )
}
