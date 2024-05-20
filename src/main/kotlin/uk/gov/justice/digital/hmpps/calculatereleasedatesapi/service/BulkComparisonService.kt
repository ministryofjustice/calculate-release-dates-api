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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
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
  private val pcscLookupService: OffenceSDSReleaseArrangementLookupService,
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
    completeComparison(comparison)
  }

  @Async
  fun processFullCaseLoadComparison(comparison: Comparison, token: String) {
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
    UserContext.setAuthToken(token)
    log.info("Using token: {}", UserContext.getAuthToken())
    val activeBookingsForPrisoners = prisonService.getActiveBookingsByPrisonerIds(prisonerIds, token)
    processCalculableSentenceEnvelopes(activeBookingsForPrisoners, comparison)
    completeComparison(comparison)
  }

  @Transactional
  fun createDiscrepancy(comparison: Comparison, comparisonPerson: ComparisonPerson, discrepancyRequest: CreateComparisonDiscrepancyRequest): ComparisonDiscrepancySummary {
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

  fun getComparisonPersonDiscrepancy(comparison: Comparison, comparisonPerson: ComparisonPerson): ComparisonDiscrepancySummary {
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
    val sentenceAndOffencesWithReleaseArrangementsForAllBookings =
      calculableSentenceEnvelopes.map { envelope ->
        pcscLookupService.populateReleaseArrangements(
          envelope.sentenceAndOffences.flatMap { sentenceAndOffences ->
            sentenceAndOffences.offences.map { offence -> NormalisedSentenceAndOffence(sentenceAndOffences, offence) }
          },
        )
      }.flatten()
    calculableSentenceEnvelopes.forEach { calculableSentenceEnvelope ->
      val sentenceAndOffencesWithReleaseArrangementsForBooking = sentenceAndOffencesWithReleaseArrangementsForAllBookings.filter { it.bookingId == calculableSentenceEnvelope.bookingId }
      val sdsPlusSentenceAndOffences = sentenceAndOffencesWithReleaseArrangementsForBooking.filter { it.isSDSPlus }
      val mismatch = buildMismatch(calculableSentenceEnvelope, sentenceAndOffencesWithReleaseArrangementsForBooking)
      val hdced4PlusDate = getHdced4PlusDate(mismatch)

      if (comparison.shouldStoreMismatch(mismatch, hdced4PlusDate != null)) {
        val establishmentValue = if (comparison.comparisonType != ComparisonType.MANUAL) {
          if (establishment == null || establishment == "") {
            comparison.prison!!
          } else {
            establishment
          }
        } else {
          null
        }
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
            sdsPlusSentencesIdentified = objectMapper.valueToTree(sdsPlusSentenceAndOffences),
            hdcedFourPlusDate = hdced4PlusDate,
            establishment = establishmentValue,
          ),
        )
      }
    }
    comparison.numberOfPeopleCompared += calculableSentenceEnvelopes.size.toLong()
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

  fun buildMismatch(
    calculableSentenceEnvelope: CalculableSentenceEnvelope,
    sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): Mismatch {
    val validationResult = validate(calculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements)
    val mismatchType = determineMismatchType(validationResult, calculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements)

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
      if (isPotentialHdc4PlusUnsupportedSentenceType(calculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements)) {
        return MismatchType.UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS
      }
      return MismatchType.UNSUPPORTED_SENTENCE_TYPE
    }

    if (isPotentialHdc4Plus(calculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements)) {
      return MismatchType.VALIDATION_ERROR_HDC4_PLUS
    }

    return MismatchType.VALIDATION_ERROR
  }

  private fun validate(calculableSentenceEnvelope: CalculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>): ValidationResult {
    val calculationUserInput = CalculationUserInputs(
      listOf(),
      calculableSentenceEnvelope.sentenceCalcDates?.earlyRemovalSchemeEligibilityDate != null,
      true,
    )

    val prisonApiSourceData: PrisonApiSourceData = this.convert(calculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements)

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

  private fun convert(source: CalculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>): PrisonApiSourceData {
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
    )
  }

  private fun isPotentialHdc4Plus(calculableSentenceEnvelope: CalculableSentenceEnvelope, sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>): Boolean {
    if (calculableSentenceEnvelope.person.isActiveSexOffender()) {
      return false
    }

    if (!hasSdsSentenceWithValidHdc4Duration(sentenceAndOffenceWithReleaseArrangements) && !hasConsecutiveSdsSentencesWithValidHdc4Duration(sentenceAndOffenceWithReleaseArrangements)) {
      return false
    }

    if (hasEdsOrSopcConsecutiveToSds(calculableSentenceEnvelope)) {
      return false
    }

    return true
  }

  private fun isPotentialHdc4PlusUnsupportedSentenceType(
    calculableSentenceEnvelope: CalculableSentenceEnvelope,
    sentenceAndOffencesWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): Boolean {
    if (calculableSentenceEnvelope.person.isActiveSexOffender()) {
      return false
    }

    if (hasUnsupportedIndeterminateSentence(calculableSentenceEnvelope)) {
      return false
    }

    val hasValidSdsSentence =
      hasNonSdsPlusSentenceWithValidHdc4Duration(sentenceAndOffencesWithReleaseArrangements)
    if (!hasValidSdsSentence && !hasConsecutiveNonSdsPlusSentencesWithValidHdc4Duration(sentenceAndOffencesWithReleaseArrangements)) {
      return false
    }

    if (hasEdsOrSopcConsecutiveToSds(calculableSentenceEnvelope)) {
      return false
    }

    return true
  }

  private fun hasUnsupportedIndeterminateSentence(calculableSentenceEnvelope: CalculableSentenceEnvelope): Boolean {
    val unsupportedSentences = calculableSentenceEnvelope.sentenceAndOffences.filter {
      !SentenceCalculationType.isSupported(it.sentenceCalculationType)
    }

    val unsupportedIndeterminateSentences = unsupportedSentences.filter {
      SentenceCalculationType.isIndeterminate(it.sentenceCalculationType)
    }

    return unsupportedIndeterminateSentences.isNotEmpty()
  }

  private fun isHdc4PlusSentenceType(sentenceCalculationType: String): Boolean {
    return try {
      return SentenceCalculationType.from(sentenceCalculationType) in HDC4_PLUS_SENTENCE_TYPES
    } catch (error: IllegalArgumentException) {
      false
    }
  }

  private fun sentenceHasValidHdc4PlusDuration(sentence: SentenceAndOffenceWithReleaseArrangements): Boolean {
    val validDuration = sentence.terms.any { term ->
      val duration = Duration(
        mapOf(
          ChronoUnit.YEARS to term.years.toLong(),
          ChronoUnit.MONTHS to term.months.toLong(),
          ChronoUnit.DAYS to term.days.toLong(),
          ChronoUnit.WEEKS to term.weeks.toLong(),
        ),
      )
      isValidHdc4PlusDuration(sentence.sentenceDate, duration)
    }
    return validDuration
  }

  private fun isValidHdc4PlusDuration(sentenceDate: LocalDate, duration: Duration): Boolean {
    val daysInFourYears = 1460
    return duration.getLengthInDays(sentenceDate) >= daysInFourYears
  }

  private fun hasSdsSentenceWithValidHdc4Duration(sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): Boolean {
    val sdsSentencesWithValidDuration = sentenceAndOffences
      .filter { isHdc4PlusSentenceType(it.sentenceCalculationType) }
      .filter { sentence -> sentenceHasValidHdc4PlusDuration(sentence) }
    return sdsSentencesWithValidDuration.isNotEmpty()
  }

  private fun hasNonSdsPlusSentenceWithValidHdc4Duration(
    sentencesAndOffencesWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): Boolean {
    val sdsSentencesWithValidDuration = sentencesAndOffencesWithReleaseArrangements
      .filter { isHdc4PlusSentenceType(it.sentenceCalculationType) }
      .filter { sentence -> sentenceHasValidHdc4PlusDuration(sentence) }
      .filter { !it.isSDSPlus }

    return sdsSentencesWithValidDuration.isNotEmpty()
  }

  private fun hasConsecutiveNonSdsPlusSentencesWithValidHdc4Duration(
    sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): Boolean {
    val consecutiveSdsSentenceChains =
      createConsecutiveSdsSentencesForHdc4(sentenceAndOffenceWithReleaseArrangements)

    val nonSdsPlusChains = consecutiveSdsSentenceChains.filter { sentenceChain ->
      sentenceChain.none { it.isSDSPlus }
    }
    return nonSdsPlusChains.any { sentenceChain ->
      val sentenceStartDate = sentenceChain.minBy { sentence -> sentence.sentenceDate }.sentenceDate
      val totalDuration = getCombinedDuration(sentenceChain)
      isValidHdc4PlusDuration(sentenceStartDate, totalDuration)
    }
  }

  private fun hasConsecutiveSdsSentencesWithValidHdc4Duration(sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>): Boolean {
    val consecutiveSdsSentenceChains =
      createConsecutiveSdsSentencesForHdc4(sentenceAndOffenceWithReleaseArrangements)
    return consecutiveSdsSentenceChains.any { sentenceChain ->
      val sentenceStartDate = sentenceChain.minBy { sentence -> sentence.sentenceDate }.sentenceDate
      val totalDuration = getCombinedDuration(sentenceChain)
      isValidHdc4PlusDuration(sentenceStartDate, totalDuration)
    }
  }

  private fun getCombinedDuration(consecutiveSentences: List<SentenceAndOffence>): Duration {
    val totalDuration = consecutiveSentences.map {
      val sentenceTerms = it.terms[0]
      Duration(
        mapOf(
          ChronoUnit.YEARS to sentenceTerms.years.toLong(),
          ChronoUnit.MONTHS to sentenceTerms.months.toLong(),
          ChronoUnit.DAYS to sentenceTerms.days.toLong(),
          ChronoUnit.WEEKS to sentenceTerms.weeks.toLong(),
        ),
      )
    }.reduce { acc, duration -> acc.appendAll(duration.durationElements) }
    return totalDuration
  }

  private fun hasEdsOrSopcConsecutiveToSds(calculableSentenceEnvelope: CalculableSentenceEnvelope): Boolean {
    val edsAndSopcSentenceTypes = EDS_SENTENCE_TYPES + SOPC_SENTENCE_TYPES
    val sentenceAndOffences = calculableSentenceEnvelope.sentenceAndOffences
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

  private fun createConsecutiveSdsSentencesForHdc4(sentencesAndOffences: List<SentenceAndOffenceWithReleaseArrangements>): List<MutableList<SentenceAndOffenceWithReleaseArrangements>> {
    val applicableSentences = sentencesAndOffences.filter { isHdc4PlusSentenceType(it.sentenceCalculationType) }

    val (baseSentences, consecutiveSentences) = applicableSentences.partition { it.consecutiveToSequence == null }
    val baseSentenceToConsecutiveSentencesMap: Map<Int, List<SentenceAndOffenceWithReleaseArrangements>> = consecutiveSentences.groupBy {
      it.consecutiveToSequence!!
    }

    val sentenceChains: MutableList<MutableList<SentenceAndOffenceWithReleaseArrangements>> = mutableListOf(mutableListOf())
    baseSentences.forEach {
      val sentenceChain: MutableList<SentenceAndOffenceWithReleaseArrangements> = mutableListOf()
      sentenceChains.add(sentenceChain)
      sentenceChain.add(it)
      createSentenceChain(it, sentenceChain, baseSentenceToConsecutiveSentencesMap, sentenceChains)
    }

    return sentenceChains.filter { it.size > 1 }
  }

  private fun createSentenceChain(
    start: SentenceAndOffenceWithReleaseArrangements,
    chain: MutableList<SentenceAndOffenceWithReleaseArrangements>,
    baseSentencesToConsecutiveSentencesMap: Map<Int, List<SentenceAndOffenceWithReleaseArrangements>>,
    chains: MutableList<MutableList<SentenceAndOffenceWithReleaseArrangements>> = mutableListOf(mutableListOf()),
  ) {
    val originalSentenceChain = chain.toMutableList()
    baseSentencesToConsecutiveSentencesMap[start.sentenceSequence]?.forEachIndexed { index, it ->
      if (index == 0) {
        chain.add(it)
        createSentenceChain(it, chain, baseSentencesToConsecutiveSentencesMap, chains)
      } else {
        // This sentence has two sentences consecutive to it. This is not allowed in practice, however it can happen
        // when a sentence in NOMIS has multiple offices, which means it becomes multiple sentences in our model.
        val chainCopy = originalSentenceChain.toMutableList()
        chains.add(chainCopy)
        chainCopy.add(it)
        createSentenceChain(it, chainCopy, baseSentencesToConsecutiveSentencesMap, chains)
      }
    }
  }

  private fun completeComparison(comparison: Comparison) {
    comparison.comparisonStatus = ComparisonStatus(comparisonStatusValue = ComparisonStatusValue.COMPLETED)
    comparisonRepository.save(comparison)
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

  private fun Comparison.shouldStoreMismatch(mismatch: Mismatch, hasHdced: Boolean): Boolean {
    if (comparisonType == ComparisonType.ESTABLISHMENT_HDCED4PLUS) {
      return mismatch.type == MismatchType.VALIDATION_ERROR_HDC4_PLUS || mismatch.type == MismatchType.UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS || hasHdced
    }

    return true
  }
}
