package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import java.time.LocalDate

class ExternalMovementTimeline(private val externalMovements: List<ExternalMovement>) {

  private val timeline: Map<LocalDate, OutOfPrisonStatus?> = externalMovements.associate { externalMovement ->
    val date = externalMovement.movementDate
    val outOfPrisonStatus: OutOfPrisonStatus? = if (externalMovement.direction == ExternalMovementDirection.OUT) {
      val nextExternalMovement = externalMovements.firstOrNull { it.movementDate > date }
      OutOfPrisonStatus(
        release = externalMovement,
        admission = nextExternalMovement,
      )
    } else {
      null
    }
    date to outOfPrisonStatus
  }.toSortedMap(compareByDescending { it })

  fun stateChangeOnDate(date: LocalDate): OutOfPrisonStatus? {
    require(timeline.containsKey(date)) { "Use statusBeforeDate unless you specifically know there is a state change on this exact date" }
    return timeline[date]
  }

  fun statusBeforeDate(date: LocalDate): OutOfPrisonStatus? {
    val previousMovement = timeline.keys.firstOrNull { it < date }
    return previousMovement?.let { timeline[it] }
  }
}
