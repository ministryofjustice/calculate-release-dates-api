package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.codec.DecodingException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoValidReturnToCustodyDateException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculablePrisoner
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.PrisonerInPrisonSummary

@Service
class PrisonService(
  private val prisonApiClient: PrisonApiClient,
  private val releaseArrangementLookupService: ReleaseArrangementLookupService,
  private val featureToggles: FeatureToggles,
) {

  fun getExternalMovements(
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
    prisonerId: String,
  ): List<PrisonApiExternalMovement> {
    if (featureToggles.externalMovementsEnabled) {
      val earliestSentenceDate = sentenceAndOffences.minOfOrNull { it.sentenceDate }
      if (earliestSentenceDate != null) {
        return prisonApiClient.getExternalMovements(prisonerId, earliestSentenceDate)
      }
    }
    return emptyList()
  }

  fun getFixedTermRecallDetails(
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

  fun getOffenderFinePayments(bookingId: Long) = prisonApiClient.getOffenderFinePayments(bookingId)

  fun getOffenderDetail(prisonerId: String): PrisonerDetails = prisonApiClient.getOffenderDetail(prisonerId)

  fun getBookingAndSentenceAdjustments(bookingId: Long, filterActive: Boolean = true): BookingAndSentenceAdjustments {
    val adjustments = prisonApiClient.getSentenceAndBookingAdjustments(bookingId)
    return BookingAndSentenceAdjustments(
      sentenceAdjustments = adjustments.sentenceAdjustments.filter { (!filterActive || it.active) && it.numberOfDays > 0 }.map { it.apply { it.bookingId = bookingId } },
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

  fun getPrisonerInPrisonSummary(prisonerId: String): PrisonerInPrisonSummary = prisonApiClient.getPrisonerInPrisonSummary(prisonerId)

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
