package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import java.time.LocalDate
@Suppress("MagicNumber")
object ImportantDates {
  val ORA_DATE: LocalDate = LocalDate.of(2015, 2, 1)
  val CJA_DATE: LocalDate = LocalDate.of(2005, 4, 4)
  val LASPO_DATE: LocalDate = LocalDate.of(2012, 12, 3)
  val SDS_PLUS_COMMENCEMENT_DATE: LocalDate = LocalDate.of(2020, 4, 1)
  val SEC_91_END_DATE: LocalDate = LocalDate.of(2020, 12, 1)
  val PCSC_COMMENCEMENT_DATE: LocalDate = LocalDate.of(2022, 6, 28)
  val EDS18_SENTENCE_TYPES_START_DATE: LocalDate = LocalDate.of(2020, 12, 1)
  val LASPO_AR_SENTENCE_TYPES_END_DATE: LocalDate = LocalDate.of(2015, 4, 13)
  val A_FINE_TEN_MILLION_FULL_RELEASE_DATE: LocalDate = LocalDate.of(2015, 6, 1)
  val SDS_DYO_TORERA_START_DATE: LocalDate = LocalDate.of(2005, 4, 4)
  val SOPC_TORERA_END_DATE: LocalDate = LocalDate.of(2022, 6, 28)
  val ERS_STOP_CLOCK_COMMENCEMENT: LocalDate = LocalDate.of(2022, 6, 28)
  val SDS_40_COMMENCEMENT_DATE = LocalDate.of(2024, 9, 10)
  val SENTENCING_ACT_2020_COMMENCEMENT: LocalDate = LocalDate.of(2020, 12, 1)
  val HDC_365_COMMENCEMENT_DATE: LocalDate = LocalDate.of(2025, 6, 3)
  val SHPO_BREACH_OFFENCE_FROM_DATE: LocalDate = LocalDate.of(2020, 12, 1)
  val FTR_48_COMMENCEMENT_DATE: LocalDate = LocalDate.of(2025, 9, 2)
  val ERS30_COMMENCEMENT_DATE: LocalDate = LocalDate.of(2025, 9, 23)
}
