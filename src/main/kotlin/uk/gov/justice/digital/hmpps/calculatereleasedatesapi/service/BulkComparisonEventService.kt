package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.apache.commons.text.WordUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import kotlin.jvm.optionals.getOrElse

@Service
class BulkComparisonEventService(
  private val prisonService: PrisonService,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val bulkComparisonEventPublisher: BulkComparisonEventPublisher?,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val comparisonRepository: ComparisonRepository,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val objectMapper: ObjectMapper,
  private val serviceUserService: ServiceUserService,
) {

  @Transactional
  @Async
  fun processPrisonComparison(comparisonId: Long, token: String) {
    setAuthToken(token)
    val comparison = getComparison(comparisonId)
    val prisoners = prisonService.getCalculablePrisonerByPrison(comparison.prison!!)
    sendMessages(comparison, prisoners.map { it.prisonerNumber })
    comparison.numberOfPeopleExpected = prisoners.size.toLong()
  }

  @Transactional
  @Async
  fun processFullCaseLoadComparison(comparisonId: Long, token: String) {
    setAuthToken(token)
    val comparison = getComparison(comparisonId)
    val currentUserPrisonsList = prisonService.getCurrentUserPrisonsList()
    var count = 0L
    for (prison in currentUserPrisonsList) {
      val prisoners = prisonService.getCalculablePrisonerByPrison(prison)
      sendMessages(comparison, prisoners.map { it.prisonerNumber }, prison)
      count += prisoners.size
    }
    comparison.numberOfPeopleExpected = count
  }

  @Transactional
  @Async
  fun processManualComparison(comparisonId: Long, prisonerIds: List<String>, token: String) {
    setAuthToken(token)
    val comparison = getComparison(comparisonId)
    sendMessages(comparison, prisonerIds)
    comparison.numberOfPeopleExpected = prisonerIds.size.toLong()
  }

  fun sendMessages(comparison: Comparison, calculations: List<String>, establishment: String? = null) {
    if (bulkComparisonEventPublisher == null) {
      throw IllegalStateException("Bulk comparison publisher is not configured for this environment")
    }
    bulkComparisonEventPublisher.sendMessageBatch(
      comparisonId = comparison.id,
      persons = calculations,
      establishment = establishment,
      username = serviceUserService.getUsername(),
    )
  }

  fun getComparison(comparisonId: Long): Comparison {
    return comparisonRepository.findById(comparisonId).orElseThrow {
      EntityNotFoundException("The comparison $comparisonId could not be found.")
    }
  }

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
    val count = comparisonPersonRepository.countByComparisonId(comparisonId = comparison.id)
    comparison.numberOfPeopleCompared = count
    if (comparison.numberOfPeopleCompared >= comparison.numberOfPeopleExpected) {
      comparison.comparisonStatus = ComparisonStatus(comparisonStatusValue = ComparisonStatusValue.COMPLETED)
      comparisonRepository.save(comparison)
    }
  }

  private fun calculateAndStoreResult(personId: String, comparison: Comparison, establishment: String?, username: String) {
    val bulkCalculationReason = calculationReasonRepository.findTopByIsBulkTrue().orElseThrow {
      EntityNotFoundException("The bulk calculation reason was not found.")
    }

    val existingPerson = comparisonPersonRepository.findByComparisonIdAndPerson(comparison.id, personId)
    if (existingPerson != null) {
      // Already processed and this is a retry from timeout.
      return
    }

    val sourceData = calculationSourceDataService.getCalculationSourceData(personId, InactiveDataOptions.default())

    val calculationUserInput = CalculationUserInputs(
      listOf(),
      sourceData.prisonerDetails.sentenceDetail?.earlyRemovalSchemeEligibilityDate != null,
    )

    val establishmentValue = getEstablishmentValueForComparisonPerson(comparison, establishment)

    val validationResult = try {
      calculationTransactionalService.validateAndCalculateForBulk(
        personId,
        calculationUserInput,
        bulkCalculationReason,
        sourceData,
        CalculationStatus.BULK,
        username,
      )
    } catch (e: Exception) {
      saveComparisonPersonWithFatalError(
        comparison,
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

  private fun saveComparisonPersonWithFatalError(
    comparison: Comparison,
    sourceData: CalculationSourceData,
    establishment: String?,
    lastException: Throwable,
  ) {
    val establishmentValue = getEstablishmentValueForComparisonPerson(comparison, establishment)
    val trimmedException = lastException.message?.trim()?.take(256) ?: "Exception had no message"
    log.error(
      "Failed to create comparison for ${sourceData.prisonerDetails.offenderNo} due to \"$trimmedException\"",
      lastException,
    )
    comparisonPersonRepository.save(
      ComparisonPerson(
        comparisonId = comparison.id,
        person = sourceData.prisonerDetails.offenderNo,
        lastName = WordUtils.capitalizeFully(sourceData.prisonerDetails.lastName),
        latestBookingId = sourceData.prisonerDetails.bookingId,
        isMatch = false,
        isValid = false,
        isFatal = true,
        mismatchType = MismatchType.FATAL_EXCEPTION,
        validationMessages = objectMapper.valueToTree(emptyList<ValidationMessage>()),
        calculatedByUsername = comparison.calculatedByUsername,
        nomisDates = sourceData.prisonerDetails.sentenceDetail?.let { objectMapper.valueToTree(it.toCalculatedMap()) }
          ?: objectMapper.createObjectNode(),
        overrideDates = sourceData.prisonerDetails.sentenceDetail?.let { objectMapper.valueToTree(it.toOverrideMap()) }
          ?: objectMapper.createObjectNode(),
        breakdownByReleaseDateType = objectMapper.createObjectNode(),
        isActiveSexOffender = sourceData.prisonerDetails.isActiveSexOffender(),
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

  fun setAuthToken(token: String) {
    UserContext.setAuthToken(token)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
