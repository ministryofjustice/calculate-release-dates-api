package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Mismatch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculableSentenceEnvelopeVersion2
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository

@Service
@ConditionalOnProperty(value = ["bulk.calculation.process"], havingValue = "SQS", matchIfMissing = false)
class BulkComparisonEventService(
  private val prisonService: PrisonService,
  private val bulkComparisonEventPublisher: BulkComparisonEventPublisher,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val comparisonRepository: ComparisonRepository,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val objectMapper: ObjectMapper,
  private val serviceUserService: ServiceUserService,
) : BulkComparisonService {

  @Transactional
  @Async
  override fun processPrisonComparison(comparison: Comparison, token: String) {
    setAuthToken(token)
    val activeBookingsAtEstablishment = prisonService.getActiveBookingsByEstablishmentVersion2(comparison.prison!!)
    sendMessages(comparison, activeBookingsAtEstablishment.map { it to null })
  }

  @Transactional
  @Async
  override fun processFullCaseLoadComparison(comparison: Comparison, token: String) {
    setAuthToken(token)
    val currentUserPrisonsList = prisonService.getCurrentUserPrisonsList()
    val activeBookingsAtEstablishment = mutableListOf<Pair<CalculableSentenceEnvelopeVersion2, String>>()
    for (prison in currentUserPrisonsList) {
      activeBookingsAtEstablishment.addAll(
        prisonService.getActiveBookingsByEstablishmentVersion2(prison).map { it to prison },
      )
    }
    sendMessages(comparison, activeBookingsAtEstablishment)
  }

  @Transactional
  @Async
  override fun processManualComparison(comparison: Comparison, prisonerIds: List<String>, token: String) {
    setAuthToken(token)
    val activeBookingsForPrisoners = prisonService.getActiveBookingsByPrisonerIdsVersion2(prisonerIds)
    sendMessages(comparison, activeBookingsForPrisoners.map { it to null })
  }

  fun sendMessages(comparison: Comparison, calculations: List<Pair<CalculableSentenceEnvelopeVersion2, String?>>) {
    // TODO refactor this after removing single process service. The comparison passed into this service is not attached to a transaction.
    val dbComparison = comparisonRepository.findById(comparison.id).orElseThrow {
      EntityNotFoundException("The comparison ${comparison.id} could not be found.")
    }
    dbComparison.numberOfPeopleExpected = calculations.size.toLong()
    calculations.forEach {
      bulkComparisonEventPublisher.sendMessage(
        comparisonId = comparison.id,
        person = it.first,
        totalToCompare = calculations.size,
        establishment = it.second,
        username = serviceUserService.getUsername(),
      )
    }
  }

  @Transactional
  fun handleBulkComparisonMessage(message: InternalMessage<BulkComparisonMessageBody>) {
    val personId = message.body.personId
    val comparison = comparisonRepository.findById(message.body.comparisonId).orElseThrow {
      EntityNotFoundException("The comparison ${message.body.comparisonId} could not be found.")
    }
    val establishment = message.body.establishment

    calculateAndStoreResult(personId, comparison, establishment, message.body.username)
  }

  @Transactional
  fun updateCountsAndCheckIfComparisonIsComplete(message: InternalMessage<BulkComparisonMessageBody>) {
    val totalToCompare = message.body.totalToCompare
    val comparison = comparisonRepository.findById(message.body.comparisonId).orElseThrow {
      EntityNotFoundException("The comparison ${message.body.comparisonId} could not be found.")
    }
    val count = comparisonPersonRepository.countByComparisonId(comparisonId = comparison.id)
    comparison.numberOfPeopleCompared = count
    if (comparison.numberOfPeopleCompared >= totalToCompare.toLong()) {
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

    val sourceData = prisonService.getPrisonApiSourceData(personId, InactiveDataOptions.default())

    val calculationUserInput = CalculationUserInputs(
      listOf(),
      sourceData.prisonerDetails.sentenceDetail?.earlyRemovalSchemeEligibilityDate != null,
    )

    val validationResult = calculationTransactionalService.validateAndCalculate(
      personId,
      calculationUserInput,
      bulkCalculationReason,
      sourceData,
      CalculationStatus.TEST,
      username,
    )

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

    val establishmentValue = getEstablishmentValueForComparisonPerson(comparison, establishment)
    comparisonPersonRepository.save(
      ComparisonPerson(
        comparisonId = comparison.id,
        person = personId,
        lastName = sourceData.prisonerDetails.lastName,
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
}
