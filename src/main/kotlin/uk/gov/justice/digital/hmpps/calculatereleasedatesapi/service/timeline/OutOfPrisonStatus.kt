package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovement

data class OutOfPrisonStatus(
  val release: ExternalMovement,
  val admission: ExternalMovement?,
)
