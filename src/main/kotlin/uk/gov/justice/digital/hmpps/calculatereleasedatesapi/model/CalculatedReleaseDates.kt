package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import java.time.LocalDate
import java.time.Period
import java.util.UUID

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
  val calculationReference: UUID,
) {
  fun toSentenceCalcDates(): SentenceCalcDates {
    return SentenceCalcDates(
      this.dates[ReleaseDateType.SLED] ?: this.dates[ReleaseDateType.SED],
      this.dates[ReleaseDateType.ARD],
      this.dates[ReleaseDateType.CRD],
      this.dates[ReleaseDateType.NPD],
      this.dates[ReleaseDateType.PRRD],
      this.dates[ReleaseDateType.SLED] ?: this.dates[ReleaseDateType.LED],
      this.dates[ReleaseDateType.HDCED],
      this.dates[ReleaseDateType.PED],
      this.dates[ReleaseDateType.HDCAD],
      this.dates[ReleaseDateType.APD],
      this.dates[ReleaseDateType.ROTL],
      this.dates[ReleaseDateType.ERSED],
      this.dates[ReleaseDateType.ETD],
      this.dates[ReleaseDateType.MTD],
      this.dates[ReleaseDateType.LTD],
      this.dates[ReleaseDateType.TUSED],
      this.dates[ReleaseDateType.Tariff],
      this.dates[ReleaseDateType.DPRRD],
      this.dates[ReleaseDateType.TERSED],
      this.dates[ReleaseDateType.ESED],
    )
  }
}
