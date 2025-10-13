package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.apache.commons.text.WordUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Mismatch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.eligibility.ErsedEligibilityService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
import kotlin.jvm.optionals.getOrElse

@Service
class BulkComparisonEventHandlerService(
  private val prisonService: PrisonService,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val comparisonRepository: ComparisonRepository,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val objectMapper: ObjectMapper,
  private val ersedEligibilityService: ErsedEligibilityService,
  private val validationService: ValidationService,
  private val bookingService: BookingService,
) {

  @Transactional
  fun handleBulkComparisonMessage(message: InternalMessage<BulkComparisonMessageBody>) {
    val personId = message.body.personId
    val comparison = comparisonRepository.findById(message.body.comparisonId).getOrElse {
      log.error("Couldn't handle message for comparison ${message.body.comparisonId}")
      return
    }
    val establishment = message.body.establishment

    calculateAndStoreResult(personId, comparison, establishment, message.body.username)
  }

  @Transactional
  fun updateCountsAndCheckIfComparisonIsComplete(message: InternalMessage<BulkComparisonMessageBody>) {
    val comparison = comparisonRepository.findById(message.body.comparisonId).getOrElse {
      log.error("Couldn't handle message for comparison ${message.body.comparisonId}")
      return
    }
    if (comparison.comparisonStatus == ComparisonStatus.PROCESSING) {
      val count = comparisonPersonRepository.countByComparisonId(comparisonId = comparison.id)
      comparison.numberOfPeopleCompared = count
      if (comparison.numberOfPeopleCompared >= comparison.numberOfPeopleExpected) {
        comparison.comparisonStatus = ComparisonStatus.COMPLETED
        comparisonRepository.save(comparison)
      }
    }
  }

  private fun calculateAndStoreResult(
    personId: String,
    comparison: Comparison,
    establishment: String?,
    username: String,
  ) {
    val bulkCalculationReason = calculationReasonRepository.findTopByIsBulkTrue().orElseThrow {
      EntityNotFoundException("The bulk calculation reason was not found.")
    }

    val existingPerson = comparisonPersonRepository.findByComparisonIdAndPerson(comparison.id, personId)
    if (existingPerson.isNotEmpty()) {
      // Already processed and this is a retry from timeout.
      return
    }
    val prisonerDetails = try {
      prisonService.getOffenderDetail(personId)
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        saveComparisonPersonWithFatalError(
          comparison,
          personId,
          null,
          "",
          e,
        )
        return
      } else {
        throw e
      }
    }

    val sourceData =
      calculationSourceDataService.getCalculationSourceData(prisonerDetails, InactiveDataOptions.default())

    val calculationUserInput = CalculationUserInputs(
      listOf(),
      hasExistingErsed(sourceData) && isEligibleForErsed(sourceData),
    )

    val establishmentValue = getEstablishmentValueForComparisonPerson(comparison, establishment)

    val validationResult = try {
      val messages =
        validationService.validate(sourceData, calculationUserInput, ValidationOrder.allValidations()).map { it ->
          if (it.code == ValidationCode.CONCURRENT_CONSECUTIVE_SENTENCES_DURATION) {
            ValidationMessage(ValidationCode.CONCURRENT_CONSECUTIVE_SENTENCES_NOTIFICATION)
          } else {
            it
          }
        }
      if (messages.isNotEmpty()) {
        ValidationResult(messages, null, null, null)
      } else {
        val booking = bookingService.getBooking(sourceData)
        val calculatedReleaseDates = calculationTransactionalService.calculate(
          booking,
          CalculationStatus.BULK,
          sourceData,
          bulkCalculationReason,
          calculationUserInput,
          historicalTusedSource = sourceData.historicalTusedData?.historicalTusedSource,
          usernameOverride = username,
        )
        ValidationResult(
          messages,
          booking,
          calculatedReleaseDates,
          calculatedReleaseDates.calculationOutput?.calculationResult,
        )
      }
    } catch (e: Exception) {
      saveComparisonPersonWithFatalError(
        comparison,
        personId,
        sourceData,
        establishmentValue,
        e,
      )
      return
    }

    val sdsPlusSentenceAndOffences = sourceData.sentenceAndOffences.filter { it.isSDSPlus }

    val mismatchType =
      determineMismatchType(validationResult, sourceData.prisonerDetails.sentenceDetail, sourceData.sentenceAndOffences)

    val mismatch = Mismatch(
      isMatch = mismatchType == MismatchType.NONE,
      isValid = validationResult.messages.isEmpty(),
      calculableSentenceEnvelope = null,
      calculatedReleaseDates = validationResult.calculatedReleaseDates,
      calculationResult = validationResult.calculationResult,
      type = mismatchType,
      messages = validationResult.messages,
    )

    comparisonPersonRepository.save(
      ComparisonPerson(
        comparisonId = comparison.id,
        person = personId,
        lastName = WordUtils.capitalizeFully(sourceData.prisonerDetails.lastName),
        latestBookingId = sourceData.prisonerDetails.bookingId,
        isMatch = mismatch.isMatch,
        isValid = mismatch.isValid,
        isFatal = false,
        mismatchType = mismatch.type,
        validationMessages = objectMapper.valueToTree(mismatch.messages),
        calculatedByUsername = comparison.calculatedByUsername,
        calculationRequestId = mismatch.calculatedReleaseDates?.calculationRequestId,
        nomisDates = sourceData.prisonerDetails.sentenceDetail?.let { objectMapper.valueToTree(it.toCalculatedMap()) }
          ?: objectMapper.createObjectNode(),
        overrideDates = sourceData.prisonerDetails.sentenceDetail?.let { objectMapper.valueToTree(it.toOverrideMap()) }
          ?: objectMapper.createObjectNode(),
        breakdownByReleaseDateType = mismatch.calculationResult?.let { objectMapper.valueToTree(it.breakdownByReleaseDateType) }
          ?: objectMapper.createObjectNode(),
        isActiveSexOffender = validationResult.booking?.offender?.isActiveSexOffender ?: false,
        sdsPlusSentencesIdentified = objectMapper.valueToTree(sdsPlusSentenceAndOffences),
        establishment = establishmentValue,
      ),
    )
  }

  private fun hasExistingErsed(sourceData: CalculationSourceData): Boolean = sourceData.prisonerDetails.sentenceDetail?.earlyRemovalSchemeEligibilityDate != null

  private fun isEligibleForErsed(sourceData: CalculationSourceData): Boolean = ersedEligibilityService.sentenceIsEligible(sourceData.prisonerDetails.bookingId).isValid

  private fun saveComparisonPersonWithFatalError(
    comparison: Comparison,
    personId: String,
    sourceData: CalculationSourceData?,
    establishment: String?,
    lastException: Throwable,
  ) {
    val establishmentValue = getEstablishmentValueForComparisonPerson(comparison, establishment)
    val trimmedException = lastException.message?.trim()?.take(256) ?: "Exception had no message"
    log.error(
      "Failed to create comparison for $personId due to \"$trimmedException\"",
      lastException,
    )
    comparisonPersonRepository.save(
      ComparisonPerson(
        comparisonId = comparison.id,
        person = personId,
        lastName = WordUtils.capitalizeFully(sourceData?.prisonerDetails?.lastName),
        latestBookingId = sourceData?.prisonerDetails?.bookingId ?: -1L,
        isMatch = false,
        isValid = false,
        isFatal = true,
        mismatchType = MismatchType.FATAL_EXCEPTION,
        validationMessages = objectMapper.valueToTree(emptyList<ValidationMessage>()),
        calculatedByUsername = comparison.calculatedByUsername,
        nomisDates = sourceData?.prisonerDetails?.sentenceDetail?.let { objectMapper.valueToTree(it.toCalculatedMap()) }
          ?: objectMapper.createObjectNode(),
        overrideDates = sourceData?.prisonerDetails?.sentenceDetail?.let { objectMapper.valueToTree(it.toOverrideMap()) }
          ?: objectMapper.createObjectNode(),
        breakdownByReleaseDateType = objectMapper.createObjectNode(),
        isActiveSexOffender = sourceData?.prisonerDetails?.isActiveSexOffender(),
        sdsPlusSentencesIdentified = objectMapper.createObjectNode(),
        establishment = establishmentValue,
        fatalException = trimmedException,
      ),
    )
  }

  fun determineMismatchType(
    validationResult: ValidationResult,
    sentenceCalcDates: SentenceCalcDates?,
    sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): MismatchType {
    if (validationResult.messages.isEmpty()) {
      val datesMatch =
        doDatesMatch(validationResult.calculatedReleaseDates, sentenceCalcDates)
      return if (datesMatch) MismatchType.NONE else MismatchType.RELEASE_DATES_MISMATCH
    }

    val unsupportedSentenceType =
      validationResult.messages.any { it.type.isUnsupported() }
    if (unsupportedSentenceType) {
      return MismatchType.UNSUPPORTED_SENTENCE_TYPE
    }

    return MismatchType.VALIDATION_ERROR
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

  fun getEstablishmentValueForComparisonPerson(comparison: Comparison, establishment: String?): String? {
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

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
