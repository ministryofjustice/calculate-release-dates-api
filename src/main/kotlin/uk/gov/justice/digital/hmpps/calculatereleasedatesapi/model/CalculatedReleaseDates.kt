package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
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
  val calculationReason: CalculationReasonDto?,
  val otherReasonDescription: String? = null,
  val calculationDate: LocalDate?,
  val historicalTusedSource: HistoricalTusedSource? = null,
  val sdsEarlyReleaseAllocatedTranche: SDSEarlyReleaseTranche? = null,
  val sdsEarlyReleaseTranche: SDSEarlyReleaseTranche? = null,
  @JsonIgnore
  val calculationOutput: CalculationOutput? = null,
  val usedPreviouslyRecordedSLED: PreviouslyRecordedSLED? = null,
) {
  fun toSentenceCalcDates(): SentenceCalcDates = SentenceCalcDates(
    this.dates[ReleaseDateType.SLED] ?: this.dates[ReleaseDateType.SED],
    null,
    this.dates[ReleaseDateType.ARD],
    null,
    this.dates[ReleaseDateType.CRD],
    null,
    this.dates[ReleaseDateType.NPD],
    null,
    this.dates[ReleaseDateType.PRRD],
    null,
    this.dates[ReleaseDateType.SLED] ?: this.dates[ReleaseDateType.LED],
    null,
    this.dates[ReleaseDateType.HDCED],
    null,
    this.dates[ReleaseDateType.PED],
    null,
    this.dates[ReleaseDateType.HDCAD],
    this.dates[ReleaseDateType.APD],
    this.dates[ReleaseDateType.ROTL],
    this.dates[ReleaseDateType.ERSED],
    this.dates[ReleaseDateType.TERSED],
    this.dates[ReleaseDateType.Tariff],
    this.dates[ReleaseDateType.ETD],
    null,
    this.dates[ReleaseDateType.MTD],
    null,
    this.dates[ReleaseDateType.LTD],
    null,
    this.dates[ReleaseDateType.TUSED],
    null,
    this.dates[ReleaseDateType.DPRRD],
    null,
    this.dates[ReleaseDateType.ESED],
  )
}
