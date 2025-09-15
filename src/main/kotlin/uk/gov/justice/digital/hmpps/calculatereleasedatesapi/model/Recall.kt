package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class Recall(
  val recallType: RecallType,
  val revocationDate: LocalDate?,
  val returnToCustodyDate: LocalDate?
) {

}