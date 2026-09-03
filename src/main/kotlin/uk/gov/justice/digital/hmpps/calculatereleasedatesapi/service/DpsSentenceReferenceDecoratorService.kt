package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.RasSentenceReference
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.DpsSentenceReferenceFormatter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.util.UUID

/**
 * For each of the Critical validation messages, decorates
 * with a DPS style message (sets dpsMessage) to replace the old NOMIS "Court case %s NOMIS line reference %s"
 *
 * The missing RaS count number requires api calls to:
 *   1. NomisSyncMappingApiClient: bookingId + sentenceSequence -> DPS sentence UUID
 *   2. RemandAndSentencingApiClient: GET /sentence/{uuid} -> chargeNumber (Note: This is RAS's name for count)
 */
@Service
class DpsSentenceReferenceDecoratorService(
  private val nomisSyncMappingApiClient: NomisSyncMappingApiClient,
  private val remandAndSentencingApiClient: RemandAndSentencingApiClient,
  private val validationUtilities: ValidationUtilities,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun decorateCriticalMessages(
    criticalMessages: List<ValidationMessage>,
    sentenceAndOffences: List<SentenceAndOffence>,
  ): List<ValidationMessage> {
    if (criticalMessages.isEmpty()) {
      return criticalMessages
    }

    val countsMap = lookupCounts(criticalMessages, sentenceAndOffences)

    return criticalMessages.map { message ->
      val sentenceAndOffence = findSentenceAndOffence(message, sentenceAndOffences)
      if (sentenceAndOffence == null) {
        message
      } else {
        decorate(message, sentenceAndOffence, countsMap[sentenceAndOffence])
      }
    }
  }

  private fun decorate(message: ValidationMessage, sentenceAndOffence: SentenceAndOffence, count: Int?): ValidationMessage {
    val rasSentenceReference = RasSentenceReference(
      count = count,
      offenceCode = sentenceAndOffence.offence.offenceCode,
      offenceDescription = sentenceAndOffence.offence.offenceDescription,
      offenceDate = sentenceAndOffence.offence.offenceStartDate,
      caseReference = sentenceAndOffence.caseReference,
      courtName = sentenceAndOffence.courtDescription ?: sentenceAndOffence.courtId ?: "the court",
      sentencingDate = sentenceAndOffence.sentenceDate,
    )
    val dpsFormattedMessage = DpsSentenceReferenceFormatter.format(rasSentenceReference)
    return message.copy(dpsMessage = String.format(message.code.dpsMessage, dpsFormattedMessage))
  }

  private fun findSentenceAndOffence(message: ValidationMessage, sentenceAndOffences: List<SentenceAndOffence>): SentenceAndOffence? {
    return validationUtilities.findSentenceAndOffence(message.arguments, sentenceAndOffences)
  }

  /*
   * TODO DM test this e2e
   * Batches the NOMIS-mapping-service lookup for every distinct sentence referenced by the critical
   * messages into a single call, then makes one RAS call per distinct sentence.
   * If either lookup fails, the sentence is simply omitted from the result
   * map and its count falls back to null - the formatter still produces a valid (count-less) RAS message
   * using the NOMIS-sourced fields rather than failing the whole page.
   */
  private fun lookupCounts(
    criticalMessages: List<ValidationMessage>,
    sentenceAndOffences: List<SentenceAndOffence>,
  ): Map<SentenceAndOffence, Int?> {
    val relevantSentenceAndOffences = criticalMessages.mapNotNull { findSentenceAndOffence(it, sentenceAndOffences) }.distinct()
    if (relevantSentenceAndOffences.isEmpty()) {
      return emptyMap()
    }

    val nomisIds = relevantSentenceAndOffences.map { NomisSentenceId(it.bookingId, it.sentenceSequence) }
    val nomisDpsSentenceMappings = try {
      nomisSyncMappingApiClient.postNomisToDpsMappingLookup(nomisIds)
    } catch (e: Exception) {
      log.warn("Failed to look up NOMIS to DPS sentence mappings for RAS message enrichment", e)
      return emptyMap()
    }

    // Correlate each NOMIS sentence with its DPS UUID from the batched lookup response
    // then fetch that sentences RAS count
    return relevantSentenceAndOffences.associateWith { sentenceAndOffence ->
      val mapping = nomisDpsSentenceMappings.find {
        it.nomisSentenceId.nomisBookingId == sentenceAndOffence.bookingId &&
          it.nomisSentenceId.nomisSentenceSequence == sentenceAndOffence.sentenceSequence
      }
      mapping?.let { lookupCount(it.dpsSentenceId) }
    }
  }

  private fun lookupCount(dpsSentenceId: String): Int? = try {
    remandAndSentencingApiClient.getSentence(UUID.fromString(dpsSentenceId))?.chargeNumber?.toIntOrNull()
  } catch (e: Exception) {
    log.warn("Failed to look up RaS API sentence {}", dpsSentenceId, e)
    null
  }
}
