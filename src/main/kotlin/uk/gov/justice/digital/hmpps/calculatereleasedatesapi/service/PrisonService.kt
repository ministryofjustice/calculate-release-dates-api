package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.codec.DecodingException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoValidReturnToCustodyDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculablePrisoner
import java.time.LocalDate

@Service
class PrisonService(
  private val prisonApiClient: PrisonApiClient,
  private val releaseArrangementLookupService: ReleaseArrangementLookupService,
  private val botusTusedService: BotusTusedService,
  private val featureToggles: FeatureToggles,
) {
  //  The activeDataOnly flag is only used by a test endpoint (1000 calcs test, which is used to test historic data)
  fun getPrisonApiSourceData(prisonerId: String, inactiveDataOptions: InactiveDataOptions): PrisonApiSourceData {
    val prisonerDetails = getOffenderDetail(prisonerId)
    return getPrisonApiSourceData(prisonerDetails, inactiveDataOptions)
  }

  fun getPrisonApiSourceData(prisonerDetails: PrisonerDetails, inactiveDataOptions: InactiveDataOptions): PrisonApiSourceData {
    val activeOnly = inactiveDataOptions.activeOnly(featureToggles.supportInactiveSentencesAndAdjustments, prisonerDetails.agencyId)
    val sentenceAndOffences = getSentencesAndOffences(prisonerDetails.bookingId, activeOnly)
    val bookingAndSentenceAdjustments = getBookingAndSentenceAdjustments(prisonerDetails.bookingId, activeOnly)
    val bookingHasFixedTermRecall = sentenceAndOffences.any { from(it.sentenceCalculationType).recallType?.isFixedTermRecall == true }
    val (ftrDetails, returnToCustodyDate) = getFixedTermRecallDetails(prisonerDetails.bookingId, bookingHasFixedTermRecall)
    val bookingHasAFine = sentenceAndOffences.any { from(it.sentenceCalculationType).sentenceType == SentenceType.AFine }
    val offenderFinePayments = if (bookingHasAFine) prisonApiClient.getOffenderFinePayments(prisonerDetails.bookingId) else listOf()
    val tusedData = getLatestTusedDataForBotus(prisonerDetails.offenderNo).getOrNull()
    val bookingHasBotus = sentenceAndOffences.any { from(it.sentenceCalculationType).sentenceType == SentenceType.Botus }
    val historicalTusedData = if (tusedData != null && bookingHasBotus) botusTusedService.identifyTused(tusedData) else null
    val externalMovements = getExternalMovements(sentenceAndOffences, prisonerDetails)

    return PrisonApiSourceData(
      sentenceAndOffences,
      prisonerDetails,
      bookingAndSentenceAdjustments,
      offenderFinePayments,
      returnToCustodyDate,
      ftrDetails,
      historicalTusedData,
      externalMovements,
    )
  }

  private fun getExternalMovements(
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
    prisonerDetails: PrisonerDetails,
  ): List<PrisonApiExternalMovement> {
    if (featureToggles.externalMovementsEnabled) {
      val earliestSentenceDate = sentenceAndOffences.minOfOrNull { it.sentenceDate }
      if (earliestSentenceDate != null) {
        return prisonApiClient.getExternalMovements(prisonerDetails.offenderNo, earliestSentenceDate)
      }
    }
    return emptyList()
  }

  private fun getFixedTermRecallDetails(
    bookingId: Long,
    bookingHasFixedTermRecall: Boolean,
  ): Pair<FixedTermRecallDetails?, ReturnToCustodyDate?> {
    if (!bookingHasFixedTermRecall) return Pair(null, null)
    return try {
      val ftrDetails = prisonApiClient.getFixedTermRecallDetails(bookingId)
      val returnToCustodyDate = transform(ftrDetails)
      ftrDetails to returnToCustodyDate
    } catch (ex: DecodingException) {
      throw NoValidReturnToCustodyDateException("No valid Return To Custody Date found for bookingId $bookingId")
    }
  }

  fun getOffenderDetail(prisonerId: String): PrisonerDetails = prisonApiClient.getOffenderDetail(prisonerId)

  fun getBookingAndSentenceAdjustments(bookingId: Long, filterActive: Boolean = true): BookingAndSentenceAdjustments {
    val adjustments = prisonApiClient.getSentenceAndBookingAdjustments(bookingId)
    return BookingAndSentenceAdjustments(
      sentenceAdjustments = adjustments.sentenceAdjustments.filter { (!filterActive || it.active) && it.numberOfDays > 0 },
      bookingAdjustments = adjustments.bookingAdjustments.filter { (!filterActive || it.active) && it.numberOfDays > 0 },
    )
  }

  fun getSentencesAndOffences(bookingId: Long, filterActive: Boolean = true): List<SentenceAndOffenceWithReleaseArrangements> {
    // There shouldn't be multiple offences associated to a single sentence; however there are at the moment (NOMIS doesn't
    // guard against it) therefore if there are multiple offences associated with one sentence then each offence is being
    // treated as a separate sentence
    val sentencesAndOffences = prisonApiClient.getSentencesAndOffences(bookingId)
      .flatMap { sentenceAndOffences -> sentenceAndOffences.offences.map { offence -> NormalisedSentenceAndOffence(sentenceAndOffences, offence) } }
      .filter { !filterActive || it.sentenceStatus == "A" }
    return releaseArrangementLookupService.populateReleaseArrangements(sentencesAndOffences)
  }

  fun getSentenceOverrides(bookingId: Long, releaseDates: List<ReleaseDate>): List<String> {
    val sentenceDetail = prisonApiClient.getSentenceDetail(bookingId)
    val checkOverride: (LocalDate?, ReleaseDate) -> String? = { overrideDate, releaseDate ->
      if (overrideDate != null && overrideDate == releaseDate.date) {
        releaseDate.type.name
      } else {
        null
      }
    }

    return releaseDates.mapNotNull { releaseDate ->
      when (releaseDate.type) {
        ReleaseDateType.HDCED -> checkOverride(sentenceDetail.homeDetentionCurfewEligibilityOverrideDate, releaseDate)
        ReleaseDateType.CRD -> checkOverride(sentenceDetail.conditionalReleaseOverrideDate, releaseDate)
        ReleaseDateType.LED, ReleaseDateType.SLED -> checkOverride(sentenceDetail.licenceExpiryOverrideDate, releaseDate)
        ReleaseDateType.SED -> checkOverride(sentenceDetail.sentenceExpiryOverrideDate, releaseDate)
        ReleaseDateType.NPD -> checkOverride(sentenceDetail.nonParoleOverrideDate, releaseDate)
        ReleaseDateType.ARD -> checkOverride(sentenceDetail.automaticReleaseOverrideDate, releaseDate)
        ReleaseDateType.TUSED -> checkOverride(sentenceDetail.topupSupervisionExpiryOverrideDate, releaseDate)
        ReleaseDateType.PED -> checkOverride(sentenceDetail.paroleEligibilityOverrideDate, releaseDate)
        else -> null
      }
    }
  }

  fun postReleaseDates(bookingId: Long, updateOffenderDates: UpdateOffenderDates) = prisonApiClient.postReleaseDates(bookingId, updateOffenderDates)

  fun getCurrentUserPrisonsList(): List<String> = prisonApiClient.getCurrentUserCaseLoads()
    ?.filter { it.caseLoadId != "KTI" }
    ?.map { caseLoad -> caseLoad.caseLoadId }
    ?: emptyList()

  fun getCalculablePrisonerByPrison(establishmentId: String): List<CalculablePrisoner> {
    var isLastPage = false
    var pageNumber = 0
    val calculableSentenceEnvelope = mutableListOf<CalculablePrisoner>()

    while (!isLastPage) {
      val calculableSentenceEnvelopePage =
        prisonApiClient.getCalculablePrisonerByPrison(establishmentId, pageNumber)
      calculableSentenceEnvelope.addAll(calculableSentenceEnvelopePage.content)
      isLastPage = calculableSentenceEnvelopePage.isLast
      pageNumber++
    }
    return calculableSentenceEnvelope
  }

  fun getCalculationsForAPrisonerId(prisonerId: String): List<SentenceCalculationSummary> = prisonApiClient.getCalculationsForAPrisonerId(prisonerId)

  fun getAgenciesByType(type: String) = prisonApiClient.getAgenciesByType(type)

  fun getOffenderKeyDates(bookingId: Long): Either<String, OffenderKeyDates> = prisonApiClient.getOffenderKeyDates(bookingId)

  fun getNOMISCalcReasons(): List<NomisCalculationReason> = prisonApiClient.getNOMISCalcReasons()

  fun getNOMISOffenderKeyDates(offenderSentCalcId: Long): Either<String, OffenderKeyDates> = prisonApiClient.getNOMISOffenderKeyDates(offenderSentCalcId)

  fun getLatestTusedDataForBotus(nomisId: String): Either<String, NomisTusedData> = prisonApiClient.getLatestTusedDataForBotus(nomisId)

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

  fun activeOnly(featureToggle: Boolean, agencyId: String): Boolean {
    return !includeInactive(featureToggle, agencyId)
  }

  private fun includeInactive(featureToggle: Boolean, agencyId: String): Boolean {
    return if (agencyId == "OUT") {
      true
    } else if (overrideToIncludeInactiveData) {
      true
    } else {
      featureToggle
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as InactiveDataOptions

    return overrideToIncludeInactiveData == other.overrideToIncludeInactiveData
  }

  override fun hashCode(): Int {
    return overrideToIncludeInactiveData.hashCode()
  }

  companion object {
    fun default(): InactiveDataOptions = InactiveDataOptions(false)
    fun overrideToIncludeInactiveData(): InactiveDataOptions = InactiveDataOptions(true)
  }
}
