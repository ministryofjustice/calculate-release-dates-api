package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

class Offender(
  private var referenceField: String,
  private var nameField: String
) {
  val name get() = nameField
  val reference get() = referenceField
}
