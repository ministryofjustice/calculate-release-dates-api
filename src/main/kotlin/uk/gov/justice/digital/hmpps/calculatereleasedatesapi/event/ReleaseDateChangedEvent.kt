package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event

class ReleaseDateChangedEvent(
  override val eventType: String = "calculate-release-dates.prisoner.changed",
  override var description: String = "Prisoners release dates have been re-calculated",
  override val additionalInformation: CalculateReleaseDatesAdditionalInformation,
  override val version: Int = 1,
  override val personReference: PersonReference? = PersonReference(listOf(PersonReferenceIdentifier.nomisPrisonerNumber(additionalInformation.prisonerId))),
) : CalculateReleaseDatesDomainEvent()
