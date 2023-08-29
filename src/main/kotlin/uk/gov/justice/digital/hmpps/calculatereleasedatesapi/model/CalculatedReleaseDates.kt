package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import java.time.LocalDate
import java.time.Period

data class CalculatedReleaseDates(
  val dates: Map<ReleaseDateType, LocalDate?>,
  val calculationRequestId: Long,
  val bookingId: Long,
  val prisonerId: String,
  val calculationStatus: CalculationStatus,
  val calculationFragments: CalculationFragments? = null,
  // TODO. This needs refactoring. The effectiveSentenceLength comes out of the calculation engine, but its not stored.
  // Its required to be sent to NOMIS, but not required for our API.
  val effectiveSentenceLength: Period? = null,
  val calculationType: CalculationType = CalculationType.CALCULATED,
  val approvedDates: Map<ReleaseDateType, LocalDate?>? = null,
) {
  fun toSentenceCalcDates(): SentenceCalcDates {
    return SentenceCalcDates(
      this.dates.getValue(ReleaseDateType.SED),
      this.dates.getValue(ReleaseDateType.ARD),
      this.dates.getValue(ReleaseDateType.CRD),
      this.dates.getValue(ReleaseDateType.NPD),
      this.dates.getValue(ReleaseDateType.PRRD),
      this.dates.getValue(ReleaseDateType.LED),
      this.dates.getValue(ReleaseDateType.HDCED),
      this.dates.getValue(ReleaseDateType.PED),
      this.dates.getValue(ReleaseDateType.HDCAD),
      this.dates.getValue(ReleaseDateType.APD),
      this.dates.getValue(ReleaseDateType.ROTL),
      this.dates.getValue(ReleaseDateType.ERSED),
      this.dates.getValue(ReleaseDateType.ETD),
      this.dates.getValue(ReleaseDateType.MTD),
      this.dates.getValue(ReleaseDateType.LTD),
      this.dates.getValue(ReleaseDateType.TUSED),
      this.dates.getValue(ReleaseDateType.Tariff),
      this.dates.getValue(ReleaseDateType.DPRRD),
      this.dates.getValue(ReleaseDateType.TERSED),
      this.dates.getValue(ReleaseDateType.ESED),
    )
  }
}
