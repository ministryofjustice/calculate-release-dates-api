package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event

data class HMPPSDomainEvent(
  val eventType: String,
)

data class PersonReference(val identifiers: List<PersonReferenceIdentifier>) {
  fun findNomsIdentifier(): String? = this.identifiers.firstOrNull { it.type == "nomsNumber" || it.type == "NOMS" }?.value
}

data class PersonReferenceIdentifier(val type: String, val value: String) {
  companion object {
    fun nomisPrisonerNumber(value: String) = PersonReferenceIdentifier(type = "NOMS", value = value)
  }
}
