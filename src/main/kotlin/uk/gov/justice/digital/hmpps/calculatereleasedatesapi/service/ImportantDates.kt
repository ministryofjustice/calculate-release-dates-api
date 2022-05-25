package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import java.time.LocalDate
@Suppress("MagicNumber")
object ImportantDates {
  val ORA_DATE: LocalDate = LocalDate.of(2015, 2, 1)
  val CJA_DATE: LocalDate = LocalDate.of(2005, 4, 4)
  val LASPO_DATE: LocalDate = LocalDate.of(2012, 12, 3)
  val SDS_PLUS_COMMENCEMENT_DATE = LocalDate.of(2020, 4, 1)
}
