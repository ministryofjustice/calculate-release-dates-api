package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import java.time.format.DateTimeFormatter

/**
 * Builds the single replacement phrase for the old NOMIS "Court case %s NOMIS line
 * reference %s" wording, using RAS terminology instead.
 *
 * This is the first iteration and has a literal mapping to the AC's in Jira RASS-2607.
 * To be iterated on later...
 */
internal object DpsSentenceReferenceFormatter {

  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

  fun format(reference: RasSentenceReference): String {
    val count = reference.count
    val offenceDate = reference.offenceDate
    val caseReference = reference.caseReference
    val courtName = reference.courtName
    val sentencingDate = reference.sentencingDate.format(dateFormatter)
    val offence = "Offence (${reference.offenceCode} ${reference.offenceDescription})"

    return when {
      // AC1 - Count number only
      count != null && offenceDate == null && caseReference == null ->
        "Count $count on case $courtName on $sentencingDate"

      // AC2 - Count & Offence date
      count != null && offenceDate != null && caseReference == null ->
        "Count $count on case $courtName on $sentencingDate"

      // AC3 - Count & Case reference
      count != null && offenceDate == null && caseReference != null ->
        "Count $count on case $caseReference at $courtName on $sentencingDate"

      // AC4 - Offence date & Case reference
      count == null && offenceDate != null && caseReference != null ->
        "$offence committed on ${offenceDate.format(dateFormatter)} on case $caseReference at $courtName on $sentencingDate"

      // AC5 - Offence date only
      count == null && offenceDate != null && caseReference == null ->
        "$offence committed on ${offenceDate.format(dateFormatter)} at $courtName on $sentencingDate"

      // AC6 - Case reference only
      count == null && offenceDate == null && caseReference != null ->
        "$offence on case $caseReference at $courtName on $sentencingDate"

      // AC7 - Count number + Case reference + offence date
      count != null && offenceDate != null && caseReference != null ->
        "Count $count on case $caseReference at $courtName on $sentencingDate"

      // Default case, not covered by an explicit AC - rthen none of the 3 optional fields present.
      else ->
        "$offence at $courtName on $sentencingDate"
    }
  }
}
