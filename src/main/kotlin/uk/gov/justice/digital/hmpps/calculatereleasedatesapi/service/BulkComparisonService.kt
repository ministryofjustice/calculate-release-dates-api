package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType
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
      val mismatch = determineMismatchType(calculableSentenceEnvelope)
      if (comparison.shouldStoreMismatch(mismatch)) {
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
          ),
        )
      }
    }
    comparison.comparisonStatus = ComparisonStatus(comparisonStatusValue = ComparisonStatusValue.COMPLETED)
    comparison.numberOfPeopleCompared = calculableSentenceEnvelopes.size.toLong()
    comparisonRepository.save(comparison)
  }

  fun determineMismatchType(calculableSentenceEnvelope: CalculableSentenceEnvelope): Mismatch {
    val mismatchType: MismatchType

    val validationResult = validate(calculableSentenceEnvelope)
    if (validationResult.messages.isEmpty()) {
      mismatchType =
        if (identifyMismatches(validationResult.calculatedReleaseDates, calculableSentenceEnvelope.sentenceCalcDates)) {
          MismatchType.NONE
        } else {
          MismatchType.RELEASE_DATES_MISMATCH
        }
    } else {
      val unsupportedSentenceType =
        validationResult.messages.any { it.code == ValidationCode.UNSUPPORTED_SENTENCE_TYPE || it.code.validationType == ValidationType.UNSUPPORTED_CALCULATION }
      mismatchType = if (unsupportedSentenceType) {
        MismatchType.UNSUPPORTED_SENTENCE_TYPE
      } else if (isPotentialHdc4Plus(calculableSentenceEnvelope)) {
        MismatchType.VALIDATION_ERROR_HDC4_PLUS
      } else {
        MismatchType.VALIDATION_ERROR
      }
    }

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

  private fun validate(calculableSentenceEnvelope: CalculableSentenceEnvelope): ValidationResult {
    val calculationUserInput = CalculationUserInputs(
      listOf(),
      calculableSentenceEnvelope.sentenceCalcDates?.earlyRemovalSchemeEligibilityDate != null,
      true,
    )

    val prisonApiSourceData: PrisonApiSourceData = this.convert(calculableSentenceEnvelope)

    val bulkCalculationReason = calculationReasonRepository.findTopByIsBulkTrue().orElseThrow{
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

  fun isValidHdc4PlusDuration(sentence: SentenceAndOffences): Boolean {
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

  fun hasEdsOrSopcConsecutiveToSds(sentenceAndOffences: List<SentenceAndOffences>): Boolean {
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
      return mismatch.type == MismatchType.VALIDATION_ERROR_HDC4_PLUS
    }

    return true
  }
}
