package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.retry.support.RetryTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancy
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyCause
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDiscrepancySummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricalTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Mismatch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyCategoryRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType

@Service
class BulkComparisonService(
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val prisonService: PrisonService,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val objectMapper: ObjectMapper,
  private val comparisonRepository: ComparisonRepository,
  private val releaseArrangementLookupService: ReleaseArrangementLookupService,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val comparisonPersonDiscrepancyRepository: ComparisonPersonDiscrepancyRepository,
  private val comparisonPersonDiscrepancyCategoryRepository: ComparisonPersonDiscrepancyCategoryRepository,
  private var serviceUserService: ServiceUserService,
  private val botusTusedService: BotusTusedService,
  private val featureToggles: FeatureToggles,
  @Qualifier("bulkComparisonRetryTemplate")
  private val retryTemplate: RetryTemplate,
) {

  @Async
  fun processPrisonComparison(comparison: Comparison, token: String) {
    setAuthTokenAndLog(token)
    val activeBookingsAtEstablishment = prisonService.getActiveBookingsByEstablishment(comparison.prison!!, token)
    processCalculableSentenceEnvelopes(activeBookingsAtEstablishment, comparison)
    completeComparison(comparison)
  }

  @Async
  fun processFullCaseLoadComparison(comparison: Comparison, token: String) {
    setAuthTokenAndLog(token)
    val currentUserPrisonsList = prisonService.getCurrentUserPrisonsList()
    log.info("Running case load comparison with prisons: {}", currentUserPrisonsList)
    for (prison in currentUserPrisonsList) {
      val activeBookingsAtEstablishment = prisonService.getActiveBookingsByEstablishment(prison, token)
      processCalculableSentenceEnvelopes(activeBookingsAtEstablishment, comparison, prison)
    }
    completeComparison(comparison)
  }

  @Async
  fun processManualComparison(comparison: Comparison, prisonerIds: List<String>, token: String) {
    setAuthTokenAndLog(token)
    val activeBookingsForPrisoners = prisonService.getActiveBookingsByPrisonerIds(prisonerIds, token)
    processCalculableSentenceEnvelopes(activeBookingsForPrisoners, comparison)
    completeComparison(comparison)
  }

  @Transactional
  fun createDiscrepancy(
    comparison: Comparison,
    comparisonPerson: ComparisonPerson,
    discrepancyRequest: CreateComparisonDiscrepancyRequest,
  ): ComparisonDiscrepancySummary {
    val existingDiscrepancy =
      comparisonPersonDiscrepancyRepository.findTopByComparisonPersonShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(
        comparisonPerson.shortReference,
      )

    val impact = ComparisonPersonDiscrepancyImpact(discrepancyRequest.impact)
    val priority = ComparisonPersonDiscrepancyPriority(discrepancyRequest.priority)
    var discrepancy = ComparisonPersonDiscrepancy(
      comparisonPerson = comparisonPerson,
      discrepancyImpact = impact,
      discrepancyPriority = priority,
      action = discrepancyRequest.action,
      detail = discrepancyRequest.detail,
      createdBy = serviceUserService.getUsername(),
    )
    discrepancy = comparisonPersonDiscrepancyRepository.save(discrepancy)
    if (existingDiscrepancy != null) {
      existingDiscrepancy.supersededById = discrepancy.id
      comparisonPersonDiscrepancyRepository.save(existingDiscrepancy)
    }

    val discrepancyCauses = discrepancyRequest.causes.map {
      ComparisonPersonDiscrepancyCause(
        category = it.category,
        subCategory = it.subCategory,
        detail = it.other,
        discrepancy = discrepancy,
      )
    }
    comparisonPersonDiscrepancyCategoryRepository.saveAll(discrepancyCauses)
    return transform(discrepancy, discrepancyCauses)
  }

  fun getComparisonPersonDiscrepancy(
    comparison: Comparison,
    comparisonPerson: ComparisonPerson,
  ): ComparisonDiscrepancySummary {
    val discrepancy =
      comparisonPersonDiscrepancyRepository.findTopByComparisonPersonShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(
        comparisonPerson.shortReference,
      ) ?: throw EntityNotFoundException("No comparison person discrepancy was found")
    return transform(discrepancy)
  }

  private fun processCalculableSentenceEnvelopes(
    calculableSentenceEnvelopes: List<CalculableSentenceEnvelope>,
    comparison: Comparison,
    establishment: String? = "",
  ) {
    var numberOfFailures = 0
    calculableSentenceEnvelopes.forEach { envelope ->
      retryTemplate.execute<Unit, RuntimeException>(
        { processSingleEnvelope(envelope, comparison, establishment) },
        {
          numberOfFailures++
          it.lastThrowable?.let { it1 -> saveComparisonPersonWithFatalError(comparison, envelope, establishment, it1) }
        },
      )
    }
    comparison.numberOfPeopleCompared += calculableSentenceEnvelopes.size.toLong()
    comparison.numberOfPeopleComparisonFailedFor += numberOfFailures
    comparisonRepository.save(comparison)
  }

  private fun saveComparisonPersonWithFatalError(
    comparison: Comparison,
    envelope: CalculableSentenceEnvelope,
    establishment: String?,
    lastException: Throwable,
  ) {
    val establishmentValue = getEstablishmentValueForComparisonPerson(comparison, establishment)
    val trimmedException = lastException.message?.trim()?.take(256) ?: "Exception had no message"
    log.error(
      "Failed to create comparison for ${envelope.person.prisonerNumber} due to \"$trimmedException\"",
      lastException,
    )
    comparisonPersonRepository.save(
      ComparisonPerson(
        comparisonId = comparison.id,
        person = envelope.person.prisonerNumber,
        lastName = envelope.person.lastName,
        latestBookingId = envelope.bookingId,
        isMatch = false,
        isValid = false,
        isFatal = true,
        mismatchType = MismatchType.FATAL_EXCEPTION,
        validationMessages = objectMapper.valueToTree(emptyList<ValidationMessage>()),
        calculatedByUsername = comparison.calculatedByUsername,
        nomisDates = envelope.sentenceCalcDates?.let { objectMapper.valueToTree(it.toCalculatedMap()) }
          ?: objectMapper.createObjectNode(),
        overrideDates = envelope.sentenceCalcDates?.let { objectMapper.valueToTree(it.toOverrideMap()) }
          ?: objectMapper.createObjectNode(),
        breakdownByReleaseDateType = objectMapper.createObjectNode(),
        isActiveSexOffender = envelope.person.isActiveSexOffender(),
        sdsPlusSentencesIdentified = objectMapper.createObjectNode(),
        establishment = establishmentValue,
        fatalException = trimmedException,
      ),
    )
  }

  private fun processSingleEnvelope(
    envelope: CalculableSentenceEnvelope,
    comparison: Comparison,
    establishment: String?,
  ) {
    val sentenceAndOffencesWithReleaseArrangementsForBooking =
      releaseArrangementLookupService.populateReleaseArrangements(normalisedSentenceAndOffences(envelope))
    val sdsPlusSentenceAndOffences = sentenceAndOffencesWithReleaseArrangementsForBooking.filter { it.isSDSPlus }
    val mismatch = buildMismatch(envelope, sentenceAndOffencesWithReleaseArrangementsForBooking)

    if (shouldStoreMismatch()) {
      val establishmentValue = getEstablishmentValueForComparisonPerson(comparison, establishment)
      comparisonPersonRepository.save(
        ComparisonPerson(
          comparisonId = comparison.id,
          person = envelope.person.prisonerNumber,
          lastName = envelope.person.lastName,
          latestBookingId = envelope.bookingId,
          isMatch = mismatch.isMatch,
          isValid = mismatch.isValid,
          isFatal = false,
          mismatchType = mismatch.type,
          validationMessages = objectMapper.valueToTree(mismatch.messages),
          calculatedByUsername = comparison.calculatedByUsername,
          calculationRequestId = mismatch.calculatedReleaseDates?.calculationRequestId,
          nomisDates = envelope.sentenceCalcDates?.let { objectMapper.valueToTree(it.toCalculatedMap()) }
            ?: objectMapper.createObjectNode(),
          overrideDates = envelope.sentenceCalcDates?.let { objectMapper.valueToTree(it.toOverrideMap()) }
            ?: objectMapper.createObjectNode(),
          breakdownByReleaseDateType = mismatch.calculationResult?.let { objectMapper.valueToTree(it.breakdownByReleaseDateType) }
            ?: objectMapper.createObjectNode(),
          isActiveSexOffender = mismatch.calculableSentenceEnvelope.person.isActiveSexOffender(),
          sdsPlusSentencesIdentified = objectMapper.valueToTree(sdsPlusSentenceAndOffences),
          establishment = establishmentValue,
        ),
      )
    }
  }

  private fun getEstablishmentValueForComparisonPerson(comparison: Comparison, establishment: String?): String? {
    val establishmentValue = if (comparison.comparisonType != ComparisonType.MANUAL) {
      if (establishment == null || establishment == "") {
        comparison.prison!!
      } else {
        establishment
      }
    } else {
      null
    }
    return establishmentValue
  }

  private fun normalisedSentenceAndOffences(envelope: CalculableSentenceEnvelope) =
    envelope.sentenceAndOffences.flatMap { sentenceAndOffences ->
      sentenceAndOffences.offences.map { offence -> NormalisedSentenceAndOffence(sentenceAndOffences, offence) }
    }

  fun buildMismatch(
    calculableSentenceEnvelope: CalculableSentenceEnvelope,
    sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): Mismatch {
    val validationResult = validate(calculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements)
    val mismatchType =
      determineMismatchType(validationResult, calculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements)

    return Mismatch(
      isMatch = mismatchType == MismatchType.NONE,
      isValid = validationResult.messages.isEmpty(),
      calculableSentenceEnvelope = calculableSentenceEnvelope,
      calculatedReleaseDates = validationResult.calculatedReleaseDates,
      calculationResult = validationResult.calculationResult,
      type = mismatchType,
      messages = validationResult.messages,
    )
  }

  fun determineMismatchType(
    validationResult: ValidationResult,
    calculableSentenceEnvelope: CalculableSentenceEnvelope,
    sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): MismatchType {
    if (validationResult.messages.isEmpty()) {
      val datesMatch =
        doDatesMatch(validationResult.calculatedReleaseDates, calculableSentenceEnvelope.sentenceCalcDates)
      return if (datesMatch) MismatchType.NONE else MismatchType.RELEASE_DATES_MISMATCH
    }

    val unsupportedSentenceType =
      validationResult.messages.any { it.code == ValidationCode.UNSUPPORTED_SENTENCE_TYPE || it.code.validationType == ValidationType.UNSUPPORTED_CALCULATION }
    if (unsupportedSentenceType) {
      return MismatchType.UNSUPPORTED_SENTENCE_TYPE
    }

    return MismatchType.VALIDATION_ERROR
  }

  private fun validate(
    calculableSentenceEnvelope: CalculableSentenceEnvelope,
    sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): ValidationResult {
    val calculationUserInput = CalculationUserInputs(
      listOf(),
      calculableSentenceEnvelope.sentenceCalcDates?.earlyRemovalSchemeEligibilityDate != null,
      true,
    )

    val prisonApiSourceData: PrisonApiSourceData =
      this.convert(calculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements)

    val bulkCalculationReason = calculationReasonRepository.findTopByIsBulkTrue().orElseThrow {
      EntityNotFoundException("The bulk calculation reason was not found.")
    }

    return calculationTransactionalService.validateAndCalculate(
      calculableSentenceEnvelope.person.prisonerNumber,
      calculationUserInput,
      false,
      bulkCalculationReason,
      prisonApiSourceData,
    )
  }

  private fun doDatesMatch(
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

  private fun convert(
    source: CalculableSentenceEnvelope,
    sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): PrisonApiSourceData {
    val prisonerDetails = PrisonerDetails(
      bookingId = source.bookingId,
      offenderNo = source.person.prisonerNumber,
      dateOfBirth = source.person.dateOfBirth,
      agencyId = source.person.agencyId,
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
      sentenceAndOffenceWithReleaseArrangements,
      prisonerDetails,
      bookingAndSentenceAdjustments,
      offenderFinePayments,
      returnToCustodyDate,
      fixedTermRecallDetails,
      getHistoricalTusedDataForBotus(source.sentenceAndOffences, prisonerDetails.offenderNo),
      if (featureToggles.externalMovementsEnabled) source.movements else emptyList(),
    )
  }

  private fun getHistoricalTusedDataForBotus(
    sentenceAndOffences: List<PrisonApiSentenceAndOffences>,
    offenderNo: String,
  ): HistoricalTusedData? {
    return if (sentenceAndOffences.any { it.sentenceCalculationType == "BOTUS" }) {
      val nomisTusedData = prisonService.getLatestTusedDataForBotus(offenderNo).getOrNull()
      return if (nomisTusedData != null) botusTusedService.identifyTused(nomisTusedData) else null
    } else {
      null
    }
  }

  private fun completeComparison(comparison: Comparison) {
    comparison.comparisonStatus = ComparisonStatus(comparisonStatusValue = ComparisonStatusValue.COMPLETED)
    comparisonRepository.save(comparison)
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(BulkComparisonService::class.java)
  }

  private fun setAuthTokenAndLog(token: String) {
    UserContext.setAuthToken(token)
    log.info("Using token: {}", UserContext.getAuthToken())
  }

  private fun shouldStoreMismatch(): Boolean {
    return true
  }
}
