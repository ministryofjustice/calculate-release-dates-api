package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDiscrepancySummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyCategoryRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonDiscrepancyRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class BulkComparisonService(
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val prisonService: PrisonService,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val objectMapper: ObjectMapper,
  private val comparisonRepository: ComparisonRepository,
  private val pcscLookupService: OffenceSdsPlusLookupService,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val comparisonPersonDiscrepancyRepository: ComparisonPersonDiscrepancyRepository,
  private val comparisonPersonDiscrepancyCategoryRepository: ComparisonPersonDiscrepancyCategoryRepository,
  private var serviceUserService: ServiceUserService,
) {
  @Async
  fun processPrisonComparison(comparison: Comparison, token: String) {
    UserContext.setAuthToken(token)
    log.info("Using token: {}", UserContext.getAuthToken())
    val activeBookingsAtEstablishment = prisonService.getActiveBookingsByEstablishment(comparison.prison!!, token)
    processCalculableSentenceEnvelopes(activeBookingsAtEstablishment, comparison)
  }

  @Async
  fun processManualComparison(comparison: Comparison, prisonerIds: List<String>, token: String) {
    UserContext.setAuthToken(token)
    log.info("Using token: {}", UserContext.getAuthToken())
    val activeBookingsForPrisoners = prisonService.getActiveBookingsByPrisonerIds(prisonerIds, token)
    processCalculableSentenceEnvelopes(activeBookingsForPrisoners, comparison)
  }

  @Transactional
  fun createDiscrepancy(comparisonReference: String, comparisonPersonReference: String, discrepancyRequest: CreateComparisonDiscrepancyRequest): ComparisonDiscrepancySummary {
    val comparison = comparisonRepository.findByComparisonShortReference(comparisonReference) ?: throw EntityNotFoundException("No comparison results exist for comparisonReference $comparisonReference ")
    val comparisonPerson =
      comparisonPersonRepository.findByComparisonIdAndShortReference(comparison.id, comparisonPersonReference)
        ?: throw EntityNotFoundException("Could not find comparison person with reference: $comparisonReference")

    val existingDiscrepancy =
      comparisonPersonDiscrepancyRepository.findTopByComparisonPerson_ShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(
        comparisonPersonReference,
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

  fun getComparisonPersonDiscrepancy(comparisonReference: String, comparisonPersonReference: String): ComparisonDiscrepancySummary {
    val discrepancy =
      comparisonPersonDiscrepancyRepository.findTopByComparisonPerson_ShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(
        comparisonPersonReference,
      ) ?: throw EntityNotFoundException("No comparison person discrepancy was found")
    return transform(discrepancy)
  }

  private fun processCalculableSentenceEnvelopes(
    calculableSentenceEnvelopes: List<CalculableSentenceEnvelope>,
    comparison: Comparison,
  ) {
    val bookingIdToSDSMatchingSentencesAndOffences =
      pcscLookupService.populateSdsPlusMarkerForOffences(
        calculableSentenceEnvelopes.map { it.sentenceAndOffences }
          .flatten(),
      )
    calculableSentenceEnvelopes.forEach { calculableSentenceEnvelope ->
      val mismatch = buildMismatch(calculableSentenceEnvelope)
      val hdced4PlusDate = getHdced4PlusDate(mismatch)

      if (comparison.shouldStoreMismatch(mismatch)) {
        comparisonPersonRepository.save(
          ComparisonPerson(
            comparisonId = comparison.id,
            person = calculableSentenceEnvelope.person.prisonerNumber,
            lastName = calculableSentenceEnvelope.person.lastName,
            latestBookingId = calculableSentenceEnvelope.bookingId,
            isMatch = mismatch.isMatch,
            isValid = mismatch.isValid,
            mismatchType = mismatch.type,
            validationMessages = objectMapper.valueToTree(mismatch.messages),
            calculatedByUsername = comparison.calculatedByUsername,
            calculationRequestId = mismatch.calculatedReleaseDates?.calculationRequestId,
            nomisDates = calculableSentenceEnvelope.sentenceCalcDates?.let { objectMapper.valueToTree(it.toCalculatedMap()) }
              ?: objectMapper.createObjectNode(),
            overrideDates = calculableSentenceEnvelope.sentenceCalcDates?.let { objectMapper.valueToTree(it.toOverrideMap()) }
              ?: objectMapper.createObjectNode(),
            breakdownByReleaseDateType = mismatch.calculationResult?.let { objectMapper.valueToTree(it.breakdownByReleaseDateType) }
              ?: objectMapper.createObjectNode(),
            isActiveSexOffender = mismatch.calculableSentenceEnvelope.person.isActiveSexOffender(),
            sdsPlusSentencesIdentified = bookingIdToSDSMatchingSentencesAndOffences[calculableSentenceEnvelope.bookingId]?.let {
              objectMapper.valueToTree(
                bookingIdToSDSMatchingSentencesAndOffences[calculableSentenceEnvelope.bookingId],
              )
            }
              ?: objectMapper.createObjectNode(),
            hdcedFourPlusDate = hdced4PlusDate,
          ),
        )
      }
    }
    comparison.comparisonStatus = ComparisonStatus(comparisonStatusValue = ComparisonStatusValue.COMPLETED)
    comparison.numberOfPeopleCompared = calculableSentenceEnvelopes.size.toLong()
    comparisonRepository.save(comparison)
  }

  private fun getHdced4PlusDate(mismatch: Mismatch): LocalDate? {
    val hdced4Plus = mismatch.calculatedReleaseDates?.dates?.getOrDefault(ReleaseDateType.HDCED4PLUS, null)
    val hdced = mismatch.calculatedReleaseDates?.dates?.getOrDefault(ReleaseDateType.HDCED, null)
    if (hdced4Plus == null && hdced == null) {
      return null
    }
    if (hdced4Plus != null && hdced == null) {
      return hdced4Plus
    }
    return if (hdced4Plus == hdced) {
      null
    } else {
      hdced4Plus
    }
  }

  fun buildMismatch(calculableSentenceEnvelope: CalculableSentenceEnvelope): Mismatch {
    val validationResult = validate(calculableSentenceEnvelope)
    val mismatchType = determineMismatchType(validationResult, calculableSentenceEnvelope)

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

  fun determineMismatchType(validationResult: ValidationResult, calculableSentenceEnvelope: CalculableSentenceEnvelope): MismatchType {
    if (validationResult.messages.isEmpty()) {
      val datesMatch = doDatesMatch(validationResult.calculatedReleaseDates, calculableSentenceEnvelope.sentenceCalcDates)
      return if (datesMatch) MismatchType.NONE else MismatchType.RELEASE_DATES_MISMATCH
    }

    val unsupportedSentenceType =
      validationResult.messages.any { it.code == ValidationCode.UNSUPPORTED_SENTENCE_TYPE || it.code.validationType == ValidationType.UNSUPPORTED_CALCULATION }
    if (unsupportedSentenceType) {
      if (isPotentialHdc4PlusUnsupportedSentenceType(calculableSentenceEnvelope)) {
        return MismatchType.UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS
      }
      return MismatchType.UNSUPPORTED_SENTENCE_TYPE
    }

    if (isPotentialHdc4Plus(calculableSentenceEnvelope)) {
      return MismatchType.VALIDATION_ERROR_HDC4_PLUS
    }

    return MismatchType.VALIDATION_ERROR
  }

  private fun validate(calculableSentenceEnvelope: CalculableSentenceEnvelope): ValidationResult {
    val calculationUserInput = CalculationUserInputs(
      listOf(),
      calculableSentenceEnvelope.sentenceCalcDates?.earlyRemovalSchemeEligibilityDate != null,
      true,
    )

    val prisonApiSourceData: PrisonApiSourceData = this.convert(calculableSentenceEnvelope)

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

  private fun isPotentialHdc4Plus(calculableSentenceEnvelope: CalculableSentenceEnvelope): Boolean {
    if (calculableSentenceEnvelope.person.isActiveSexOffender()) {
      return false
    }

    val sentenceAndOffences = calculableSentenceEnvelope.sentenceAndOffences
    val applicableSentences =
      sentenceAndOffences.filter { SentenceCalculationType.from(it.sentenceCalculationType) in HDC4_PLUS_SENTENCE_TYPES }
    if (applicableSentences.isEmpty()) {
      return false
    }

    if (!applicableSentences.any { sentence -> isValidHdc4PlusDuration(sentence) }) {
      return false
    }

    if (hasEdsOrSopcConsecutiveToSds(sentenceAndOffences)) {
      return false
    }

    return true
  }

  private fun isPotentialHdc4PlusUnsupportedSentenceType(calculableSentenceEnvelope: CalculableSentenceEnvelope): Boolean {
    if (calculableSentenceEnvelope.person.isActiveSexOffender()) {
      return false
    }

    if (hasIndeterminateSentence(calculableSentenceEnvelope)) {
      return false
    }

    if (!calculableSentenceEnvelope.sentenceAndOffences.any { sentence -> isValidHdc4PlusDuration(sentence) }) {
      return false
    }

    return true
  }

  private fun isValidHdc4PlusDuration(sentence: SentenceAndOffences): Boolean {
    val daysInFourYears = 1460

    val validDuration = sentence.terms.any { term ->
      val duration = Duration(
        mapOf(
          ChronoUnit.YEARS to term.years.toLong(),
          ChronoUnit.MONTHS to term.months.toLong(),
          ChronoUnit.DAYS to term.days.toLong(),
          ChronoUnit.WEEKS to term.weeks.toLong(),
        ),
      )
      duration.getLengthInDays(sentence.sentenceDate) >= daysInFourYears
    }
    return validDuration
  }

  private fun hasEdsOrSopcConsecutiveToSds(sentenceAndOffences: List<SentenceAndOffences>): Boolean {
    val edsAndSopcSentenceTypes = EDS_SENTENCE_TYPES + SOPC_SENTENCE_TYPES

    val consecutiveSentences = sentenceAndOffences.filter { it.consecutiveToSequence != null }
    val consecutiveEdsOrSopcToSds =
      consecutiveSentences.filter { consecutiveSentence ->
        val consecutiveToSentence =
          sentenceAndOffences.firstOrNull { it.sentenceSequence == consecutiveSentence.consecutiveToSequence }
        if (consecutiveToSentence != null) {
          val consecutiveSentenceType = SentenceCalculationType.from(consecutiveSentence.sentenceCalculationType)
          val consecutiveToSentenceType = SentenceCalculationType.from(consecutiveToSentence.sentenceCalculationType)
          if (consecutiveSentenceType in HDC4_PLUS_SENTENCE_TYPES && consecutiveToSentenceType in edsAndSopcSentenceTypes) {
            return@filter true
          }
          if (consecutiveSentenceType in edsAndSopcSentenceTypes && consecutiveToSentenceType in HDC4_PLUS_SENTENCE_TYPES) {
            return@filter true
          }
        }
        return@filter false
      }
    return consecutiveEdsOrSopcToSds.isNotEmpty()
  }

  private fun hasIndeterminateSentence(calculableSentenceEnvelope: CalculableSentenceEnvelope): Boolean {
    return calculableSentenceEnvelope.sentenceAndOffences.any { SentenceCalculationType.isIndeterminate(it.sentenceCalculationType) }
  }

  companion object {
    val HDC4_PLUS_SENTENCE_TYPES = listOf(
      SentenceCalculationType.ADIMP,
      SentenceCalculationType.ADIMP_ORA,
      SentenceCalculationType.SEC91_03,
      SentenceCalculationType.SEC91_03_ORA,
      SentenceCalculationType.SEC250,
      SentenceCalculationType.SEC250_ORA,
      SentenceCalculationType.YOI,
      SentenceCalculationType.YOI_ORA,
    )

    val EDS_SENTENCE_TYPES = listOf(
      SentenceCalculationType.LASPO_AR,
      SentenceCalculationType.LASPO_DR,
      SentenceCalculationType.EDS18,
      SentenceCalculationType.EDS21,
      SentenceCalculationType.EDSU18,
    )

    val SOPC_SENTENCE_TYPES = listOf(
      SentenceCalculationType.SDOPCU18,
      SentenceCalculationType.SOPC18,
      SentenceCalculationType.SOPC21,
      SentenceCalculationType.SEC236A,
    )
    private val log: Logger = LoggerFactory.getLogger(BulkComparisonService::class.java)
  }

  private fun Comparison.shouldStoreMismatch(mismatch: Mismatch): Boolean {
    if (comparisonType == ComparisonType.ESTABLISHMENT_HDCED4PLUS) {
      return mismatch.type == MismatchType.VALIDATION_ERROR_HDC4_PLUS || mismatch.type == MismatchType.UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS
    }

    return true
  }
}
