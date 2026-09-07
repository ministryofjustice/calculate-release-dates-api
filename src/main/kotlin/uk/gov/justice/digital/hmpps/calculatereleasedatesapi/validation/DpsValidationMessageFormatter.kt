package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import java.time.format.DateTimeFormatter

/**
 * Builds the single replacement phrase for the old NOMIS "Court case %s NOMIS line
 * reference %s" wording, using DPS terminology instead.
 */
internal object DpsValidationMessageFormatter {

  private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  fun format(message: DpsValidationMessage): String {
    val count = message.count
    val offenceDate = message.offenceDate
    val caseReference = message.caseReference
    val courtName = message.courtName
    val sentencingDate = message.sentencingDate.format(dateFormatter)
    val offence = "${message.offenceCode} - ${message.offenceDescription}"

    return when {
      // Count number only
      count != null && offenceDate == null && caseReference == null ->
        "Count $count on case $courtName on $sentencingDate"

      // Count & Offence date
      count != null && offenceDate != null && caseReference == null ->
        "Count $count on case $courtName on $sentencingDate"

      // Count & Case reference
      count != null && offenceDate == null && caseReference != null ->
        "Count $count on case $caseReference at $courtName on $sentencingDate"

      // Offence date & Case reference
      count == null && offenceDate != null && caseReference != null ->
        "$offence committed on ${offenceDate.format(dateFormatter)} on case $caseReference at $courtName on $sentencingDate"

      // Offence date only
      count == null && offenceDate != null && caseReference == null ->
        "$offence committed on ${offenceDate.format(dateFormatter)} at $courtName on $sentencingDate"

      // Case reference only
      count == null && offenceDate == null && caseReference != null ->
        "$offence on case $caseReference at $courtName on $sentencingDate"

      // Count number + Case reference + offence date
      count != null && offenceDate != null && caseReference != null ->
        "Count $count on case $caseReference at $courtName on $sentencingDate"

      // Default case, not covered by an explicit AC
      else ->
        "$offence at $courtName on $sentencingDate"
    }
  }
}
