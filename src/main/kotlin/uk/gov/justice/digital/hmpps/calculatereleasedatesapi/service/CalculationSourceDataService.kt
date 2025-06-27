package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from

@Service
class CalculationSourceDataService(
  private val prisonService: PrisonService,
  private val featureToggles: FeatureToggles,
  private val botusTusedService: BotusTusedService,
  private val adjustmentsApiClient: AdjustmentsApiClient,
) {

  fun getCalculationSourceData(prisonerId: String, inactiveDataOptions: InactiveDataOptions, olderBookingsToInclude: List<Long> = emptyList()): CalculationSourceData {
    val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
    return getCalculationSourceData(prisonerDetails, inactiveDataOptions, olderBookingsToInclude)
  }

  fun getCalculationSourceData(prisonerDetails: PrisonerDetails, inactiveDataOptions: InactiveDataOptions, olderBookingsToInclude: List<Long> = emptyList()): CalculationSourceData {
    var data = getBookingLevelSourceData(prisonerDetails, prisonerDetails.bookingId, inactiveDataOptions)
    olderBookingsToInclude.forEach {
      data = data.appendOlderBooking(getBookingLevelSourceData(prisonerDetails, it, inactiveDataOptions, olderBooking = true))
    }
    return getCalculationSourceData(prisonerDetails, data)
  }

  private fun getBookingLevelSourceData(prisonerDetails: PrisonerDetails, bookingId: Long, inactiveDataOptions: InactiveDataOptions, olderBooking: Boolean = false): BookingLevelSourceData {
    val activeOnly = inactiveDataOptions.activeOnly(featureToggles.supportInactiveSentencesAndAdjustments)

    val sentenceAndOffences = prisonService.getSentencesAndOffences(bookingId, activeOnly)
    val adjustments = getAdjustments(prisonerDetails.offenderNo, bookingId, activeOnly, olderBooking)
    val bookingHasFixedTermRecall = sentenceAndOffences.any { from(it.sentenceCalculationType).recallType?.isFixedTermRecall == true }
    val (ftrDetails, returnToCustodyDate) = prisonService.getFixedTermRecallDetails(bookingId, bookingHasFixedTermRecall)
    val bookingHasAFine = sentenceAndOffences.any { from(it.sentenceCalculationType).sentenceType == SentenceType.AFine }
    val offenderFinePayments = if (bookingHasAFine) prisonService.getOffenderFinePayments(bookingId) else listOf()
    return BookingLevelSourceData(
      sentenceAndOffences,
      adjustments,
      offenderFinePayments,
      returnToCustodyDate,
      ftrDetails,
    )
  }

  private fun getCalculationSourceData(prisonerDetails: PrisonerDetails, bookingLevelSourceData: BookingLevelSourceData): CalculationSourceData {
    val bookingHasBotus = bookingLevelSourceData.sentenceAndOffences.any { from(it.sentenceCalculationType).sentenceType == SentenceType.Botus }
    val tusedData = if (bookingHasBotus) prisonService.getLatestTusedDataForBotus(prisonerDetails.offenderNo).getOrNull() else null
    val historicalTusedData = tusedData?.let { botusTusedService.identifyTused(it) }
    val externalMovements = prisonService.getExternalMovements(bookingLevelSourceData.sentenceAndOffences, prisonerDetails.offenderNo)

    return CalculationSourceData(
      bookingLevelSourceData.sentenceAndOffences,
      prisonerDetails,
      bookingLevelSourceData.adjustments,
      bookingLevelSourceData.offenderFinePayments,
      bookingLevelSourceData.returnToCustodyDate,
      bookingLevelSourceData.fixedTermRecallDetails,
      historicalTusedData,
      externalMovements,
    )
  }

  private fun getAdjustments(
    prisonerId: String,
    bookingId: Long,
    activeOnly: Boolean,
    isOldBooking: Boolean,
  ): AdjustmentsSourceData = if (featureToggles.useAdjustmentsApi) {
    val statuses = if (activeOnly) {
      listOf(AdjustmentDto.Status.ACTIVE)
    } else {
      listOf(AdjustmentDto.Status.ACTIVE, AdjustmentDto.Status.INACTIVE)
    }
    AdjustmentsSourceData(adjustmentsApiData = adjustmentsApiClient.getAdjustmentsByPerson(prisonerId, statuses, currentPeriodOfCustody = !isOldBooking).filter { it.bookingId == bookingId })
  } else {
    AdjustmentsSourceData(prisonApiData = prisonService.getBookingAndSentenceAdjustments(bookingId, activeOnly))
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

/**
 * Class to represent if inactive data should be included within calculation.
 *
 * 1. Inactive data is always included for OUT prisoners.
 * 2. The default behaviour is defined by a feature toggle
 * 3. Some endpoints override this behaviour because they require inactive data e.g. recalls, IR
 */
class InactiveDataOptions private constructor(
  private val overrideToIncludeInactiveData: Boolean,
) {

  fun activeOnly(featureToggle: Boolean): Boolean = !includeInactive(featureToggle)

  private fun includeInactive(featureToggle: Boolean): Boolean = if (overrideToIncludeInactiveData) {
    true
  } else {
    featureToggle
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as InactiveDataOptions

    return overrideToIncludeInactiveData == other.overrideToIncludeInactiveData
  }

  override fun hashCode(): Int = overrideToIncludeInactiveData.hashCode()

  companion object {
    fun default(): InactiveDataOptions = InactiveDataOptions(false)
    fun overrideToIncludeInactiveData(): InactiveDataOptions = InactiveDataOptions(true)
  }
}
data class BookingLevelSourceData(
  val sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
  val adjustments: AdjustmentsSourceData,
  val offenderFinePayments: List<OffenderFinePayment> = listOf(),
  val returnToCustodyDate: ReturnToCustodyDate?,
  val fixedTermRecallDetails: FixedTermRecallDetails? = null,
) {

  fun appendOlderBooking(data: BookingLevelSourceData): BookingLevelSourceData = copy(
    sentenceAndOffences = this.sentenceAndOffences + data.sentenceAndOffences,
    adjustments = this.adjustments.appendOlderBooking(data.adjustments),
    offenderFinePayments = this.offenderFinePayments + data.offenderFinePayments,
    returnToCustodyDate = this.returnToCustodyDate ?: data.returnToCustodyDate,
    fixedTermRecallDetails = this.fixedTermRecallDetails ?: data.fixedTermRecallDetails,
  )
}
