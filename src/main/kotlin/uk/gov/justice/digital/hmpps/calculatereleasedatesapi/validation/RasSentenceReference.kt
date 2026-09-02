package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import java.time.LocalDate

/**
 * RAS data class required to build out validation messages,
 * replacing the old "Court case %s NOMIS line reference %s" NOMIS wording.
 *
 * Optional - Count number
 * Mandatory - Offence code
 * Mandatory - Offence description
 * Optional - Offence date
 * Optional - Case reference
 * Mandatory - Court name
 * Mandatory - Court date (Sentencing date)
 */
internal data class RasSentenceReference(
  val count: Int?,
  val offenceCode: String,
  val offenceDescription: String,
  val offenceDate: LocalDate?,
  val caseReference: String?,
  val courtName: String,
  val sentencingDate: LocalDate,
)
