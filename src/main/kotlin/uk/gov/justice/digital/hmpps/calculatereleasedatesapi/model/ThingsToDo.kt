package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ToDoType

data class ThingsToDo(
  val prisonerId: String,
  val thingsToDo: List<ToDoType> = emptyList(),
)
