package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.remand

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.HistoricCalculationsService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RemandAndSentencingService
import java.time.LocalDate

data class OffenderCustodyPeriod(
  val startDate: LocalDate,
  val endDate: LocalDate?,
  val reason: String,
  val courtCases: List<Unit>,
  val recalls: List<Recall>,
  val immigrationDetention: List<Unit>,
  val calculations: List<HistoricCalculation>,
)

@Service
class OffenderPeriodsOfCustodyService(
  private val prisonService: PrisonService,
  private val historicCalculationsService: HistoricCalculationsService,
  private val remandAndSentencingService: RemandAndSentencingService,
) {
  fun offenderRemandPeriods(prisonerId: String): List<OffenderCustodyPeriod> {
    val externalMovements = prisonService.getExternalMovements(prisonerId)
    val periods = buildCustodyPeriods(externalMovements)
    if (periods.isEmpty()) return emptyList()

    val asyncRequest = Mono.zip(
      Mono
        .fromCallable { historicCalculationsService.getHistoricCalculationsForPrisoner(prisonerId) }
        .subscribeOn(Schedulers.boundedElastic()),
      remandAndSentencingService.getRecallsForOffender(prisonerId),
    )
      .block()!!

    val historicCalculations = asyncRequest.t1
    val offenderRecalls = asyncRequest.t2

    return periods.map { period ->
      val calculations = historicCalculations.filter {
        it.calculationDate.toLocalDate() in period.startDate..(period.endDate ?: LocalDate.MAX)
      }

      val recalls = offenderRecalls.recalls.filter {
        it.createdAt.toLocalDate() in period.startDate..(period.endDate ?: LocalDate.MAX)
      }

      OffenderCustodyPeriod(
        startDate = period.startDate,
        endDate = period.endDate,
        reason = period.reason,
        courtCases = emptyList(),
        recalls = recalls,
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

    // if admission has no end date, it must be the current custody period
    if (currentAdmission != null) {
      periods.add(
        CustodyPeriod(
          startDate = currentAdmission.movementDate,
          endDate = null,
          reason = currentAdmission.movementReason,
        ),
      )
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
    val endDate: LocalDate?,
    val reason: String,
  )
}
