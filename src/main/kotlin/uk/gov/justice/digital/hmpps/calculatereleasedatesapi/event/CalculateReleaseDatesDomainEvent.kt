package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

abstract class CalculateReleaseDatesDomainEvent {
  abstract val additionalInformation: CalculateReleaseDatesAdditionalInformation
  val occurredAt: String =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(Instant.now())
  abstract val eventType: String
  abstract val version: Int
  abstract val description: String
  abstract val personReference: PersonReference?
}
