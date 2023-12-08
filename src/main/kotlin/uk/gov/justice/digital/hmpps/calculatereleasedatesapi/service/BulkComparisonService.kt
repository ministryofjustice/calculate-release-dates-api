package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Mismatch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

@Service
class BulkComparisonService(
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val prisonService: PrisonService,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val objectMapper: ObjectMapper,
  private val comparisonRepository: ComparisonRepository,
  private val pcscLookupService: OffenceSdsPlusLookupService
) {

  @Async
  fun processPrisonComparison(comparison: Comparison) {
    val activeBookingsAtEstablishment = prisonService.getActiveBookingsByEstablishment(comparison.prison!!)
    getPCSCMarkerForOffences(activeBookingsAtEstablishment.map { it.sentenceAndOffences }.flatten())
    processCalculableSentenceEnvelopes(activeBookingsAtEstablishment, comparison)
  }

  private fun getPCSCMarkerForOffences(sentencesAndOffencesToCheck: List<SentenceAndOffences>) {
    pcscLookupService.populateSdsPlusMarkerForOffences(sentencesAndOffencesToCheck)
  }

  @Async
  fun processManualComparison(comparison: Comparison, prisonerIds: List<String>) {
    val activeBookingsForPrisoners = prisonService.getActiveBookingsByPrisonerIds(prisonerIds)
    processCalculableSentenceEnvelopes(activeBookingsForPrisoners, comparison)
  }

  private fun processCalculableSentenceEnvelopes(
    calculableSentenceEnvelopes: List<CalculableSentenceEnvelope>,
    comparison: Comparison,
  ) {
    calculableSentenceEnvelopes.forEach { calculableSentenceEnvelope ->
      val mismatch = determineMismatchType(calculableSentenceEnvelope)
      comparisonPersonRepository.save(
        ComparisonPerson(
          comparisonId = comparison.id,
          person = calculableSentenceEnvelope.person.prisonerNumber,
          latestBookingId = calculableSentenceEnvelope.bookingId,
          isMatch = mismatch.isMatch,
          isValid = mismatch.isValid,
          mismatchType = mismatch.type,
          validationMessages = objectMapper.valueToTree(mismatch.messages),
          calculatedByUsername = comparison.calculatedByUsername,
          calculationRequestId = mismatch.calculatedReleaseDates?.calculationRequestId,
          nomisDates = calculableSentenceEnvelope.sentenceCalcDates?.let { objectMapper.valueToTree(it.toCalculatedMap()) } ?: objectMapper.createObjectNode(),
          overrideDates = calculableSentenceEnvelope.sentenceCalcDates?.let { objectMapper.valueToTree(it.toOverrideMap()) } ?: objectMapper.createObjectNode(),
          breakdownByReleaseDateType = mismatch.calculationResult?.let { objectMapper.valueToTree(it.breakdownByReleaseDateType) } ?: objectMapper.createObjectNode(),
          isActiveSexOffender = mismatch.calculableSentenceEnvelope.person.isActiveSexOffender(),
        ),
      )
    }
    comparison.comparisonStatus = ComparisonStatus(comparisonStatusValue = ComparisonStatusValue.COMPLETED)
    comparison.numberOfPeopleCompared = calculableSentenceEnvelopes.size.toLong()
    comparisonRepository.save(comparison)
  }

  fun determineMismatchType(calculableSentenceEnvelope: CalculableSentenceEnvelope): Mismatch {
    val mismatch = Mismatch(
      isMatch = false,
      isValid = false,
      calculableSentenceEnvelope = calculableSentenceEnvelope,
      calculatedReleaseDates = null,
      type = MismatchType.NONE,
    )

    val calculationUserInput = CalculationUserInputs(
      listOf(),
      calculableSentenceEnvelope.sentenceCalcDates?.earlyRemovalSchemeEligibilityDate != null,
      true,
    )

    val prisonApiSourceData: PrisonApiSourceData = this.convert(calculableSentenceEnvelope)

    val validationResult = calculationTransactionalService.validateAndCalculate(
      calculableSentenceEnvelope.person.prisonerNumber,
      calculationUserInput,
      false,
      prisonApiSourceData,
    )
    mismatch.messages = validationResult.messages
    mismatch.isValid = validationResult.messages.isEmpty()
    mismatch.calculatedReleaseDates = validationResult.calculatedReleaseDates
    mismatch.calculationResult = validationResult.calculationResult

    if (mismatch.isValid) {
      val datesMatch =
        identifyMismatches(validationResult.calculatedReleaseDates, calculableSentenceEnvelope.sentenceCalcDates)
      if (datesMatch) {
        mismatch.isMatch = true
        mismatch.type = MismatchType.NONE
      } else {
        mismatch.type = MismatchType.RELEASE_DATES_MISMATCH
      }
    } else {
      val unsupportedSentenceType = validationResult.messages.any { it.code == ValidationCode.UNSUPPORTED_SENTENCE_TYPE }
      mismatch.type = if (unsupportedSentenceType) MismatchType.UNSUPPORTED_SENTENCE_TYPE else MismatchType.VALIDATION_ERROR
      mismatch.isMatch = false
    }

    return mismatch
  }

  private fun identifyMismatches(
    calculatedReleaseDates: CalculatedReleaseDates?,
    sentenceCalcDates: SentenceCalcDates?,
  ): Boolean {
    if (calculatedReleaseDates != null && calculatedReleaseDates.dates.isNotEmpty()) {
      val calculatedSentenceCalcDates = calculatedReleaseDates.toSentenceCalcDates()
      if (sentenceCalcDates != null) {
        return calculatedSentenceCalcDates.isSameComparableCalculatedDates(sentenceCalcDates)
      }
    }
    return true
  }

  private fun convert(source: CalculableSentenceEnvelope): PrisonApiSourceData {
    val prisonerDetails = PrisonerDetails(
      bookingId = source.bookingId,
      offenderNo = source.person.prisonerNumber,
      dateOfBirth = source.person.dateOfBirth,
      alerts = source.person.alerts,
    )

    val bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(
      source.bookingAdjustments.map {
        BookingAdjustment(
          it.active,
          it.fromDate,
          it.toDate,
          it.numberOfDays,
          it.type,
        )
      }.toList(),
      source.sentenceAdjustments.map {
        SentenceAdjustment(
          it.sentenceSequence!!,
          it.active,
          it.fromDate,
          it.toDate,
          it.numberOfDays!!,
          it.type!!,
        )
      }.toList(),
    )

    val offenderFinePayments = source.offenderFinePayments.map {
      OffenderFinePayment(
        it.bookingId!!,
        it.paymentDate!!,
        it.paymentAmount!!,
      )
    }

    val fixedTermRecallDetails = source.fixedTermRecallDetails?.let {
      FixedTermRecallDetails(
        it.bookingId!!,
        it.returnToCustodyDate!!,
        it.recallLength!!,
      )
    }

    val returnToCustodyDate = source.fixedTermRecallDetails?.let {
      ReturnToCustodyDate(
        it.bookingId!!,
        it.returnToCustodyDate!!,
      )
    }

    return PrisonApiSourceData(
      source.sentenceAndOffences,
      prisonerDetails,
      bookingAndSentenceAdjustments,
      offenderFinePayments,
      returnToCustodyDate,
      fixedTermRecallDetails,
    )
  }
}
