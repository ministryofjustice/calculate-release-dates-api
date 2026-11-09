package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.text.WordUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AdjustmentsApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BulkComparisonMessageBody
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.InternalMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsAdjustmentInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import kotlin.jvm.optionals.getOrElse

@Service
class UnusedDeductionsComparisonHandlerService(
  private val comparisonRepository: UnusedDeductionsComparisonRepository,
  private val comparisonPersonRepository: UnusedDeductionsComparisonPersonRepository,
  private val adjustmentsApiClient: AdjustmentsApiClient,
  private val unusedDeductionsCalculationService: UnusedDeductionsCalculationService,
  private val objectMapper: ObjectMapper,
) {

  fun handleMessage(message: InternalMessage<BulkComparisonMessageBody>) {
    val comparison = comparisonRepository.findById(message.body.comparisonId).getOrElse {
      log.error("Couldn't handle message for unused deductions comparison ${message.body.comparisonId}")
      return
    }
    if (comparisonPersonRepository.findByComparisonIdAndPerson(comparison.id(), message.body.personId).isNotEmpty()) {
      // Already processed and this is a retry from timeout.
      return
    }
    comparisonPersonRepository.save(compare(comparison, message.body.personId, message.body.establishment))
  }

  @Transactional
  fun updateCountsAndCheckIfComparisonIsComplete(comparisonId: Long) {
    val comparison = comparisonRepository.findById(comparisonId).getOrElse {
      log.error("Couldn't update counts for unused deductions comparison $comparisonId")
      return
    }
    if (comparison.comparisonStatus == ComparisonStatus.PROCESSING) {
      comparison.numberOfPeopleCompared = comparisonPersonRepository.countByComparisonId(comparisonId)
      if (comparison.numberOfPeopleCompared >= comparison.numberOfPeopleExpected) {
        comparison.numberOfPeopleComparisonFailedFor = comparisonPersonRepository.countByComparisonIdAndOutcomeIn(comparisonId, listOf(UnusedDeductionsComparisonOutcome.FATAL_ERROR))
        comparison.comparisonStatus = ComparisonStatus.COMPLETED
      }
      comparisonRepository.save(comparison)
    }
  }

  private fun compare(comparison: UnusedDeductionsComparison, person: String, establishment: String?): UnusedDeductionsComparisonPerson {
    var adjustments: List<AdjustmentDto> = emptyList()
    return try {
      adjustments = adjustmentsApiClient.getAdjustmentsByPerson(person, listOf(AdjustmentDto.Status.ACTIVE), true)
      val existing = adjustments.filter { it.adjustmentType == AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS }.sumOf { (it.days ?: 0).toLong() }
      val detail = unusedDeductionsCalculationService.calculateDetailed(adjustments, person)
      val calculated = detail.response.unusedDeductions
      val outcome = when {
        calculated == null -> UnusedDeductionsComparisonOutcome.VALIDATION_ERROR
        calculated == existing -> UnusedDeductionsComparisonOutcome.MATCH
        else -> UnusedDeductionsComparisonOutcome.MISMATCH
      }
      UnusedDeductionsComparisonPerson(
        comparisonId = comparison.id(),
        person = person,
        lastName = WordUtils.capitalizeFully(detail.prisoner?.lastName),
        latestBookingId = detail.prisoner?.bookingId,
        outcome = outcome,
        existingUnusedDeductions = existing,
        calculatedUnusedDeductions = calculated,
        custodialDays = detail.custodialDays,
        totalDeductions = detail.totalDeductions,
        adjustments = adjustmentInputs(adjustments),
        validationMessages = objectMapper.valueToTree(detail.response.validationMessages),
        establishment = establishment ?: comparison.prison,
      )
    } catch (e: Exception) {
      val trimmedException = e.message?.trim()?.take(256) ?: "Exception had no message"
      log.error("Failed to compare unused deductions for $person due to \"$trimmedException\"", e)
      UnusedDeductionsComparisonPerson(
        comparisonId = comparison.id(),
        person = person,
        outcome = UnusedDeductionsComparisonOutcome.FATAL_ERROR,
        existingUnusedDeductions = adjustments.filter { it.adjustmentType == AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS }.sumOf { (it.days ?: 0).toLong() },
        adjustments = adjustmentInputs(adjustments),
        validationMessages = objectMapper.valueToTree(emptyList<ValidationMessage>()),
        fatalException = trimmedException,
        establishment = establishment ?: comparison.prison,
      )
    }
  }

  private fun adjustmentInputs(adjustments: List<AdjustmentDto>) = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
    adjustments.map {
      UnusedDeductionsAdjustmentInput(
        id = it.id,
        adjustmentType = it.adjustmentType.name,
        source = it.source?.name,
        fromDate = it.fromDate,
        toDate = it.toDate,
        days = it.days,
        effectiveDays = it.effectiveDays,
      )
    },
  )

  private companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }
}
