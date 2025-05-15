package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TermType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.ConsecutiveSentenceUtil
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.collections.plusAssign
import kotlin.to

@Service
class SentenceValidationService(
  private val validationUtilities: ValidationUtilities,
  private val extractionService: SentencesExtractionService,
  private val section91ValidationService: Section91ValidationService,
  private val sopcValidationService: SOPCValidationService,
  private val fineValidationService: FineValidationService,
  private val edsValidationService: EDSValidationService,
  private val featuresToggles: FeatureToggles,
) {

  internal fun validateSentences(sentences: List<SentenceAndOffence>, bulkCalcValidation: Boolean = false): MutableList<ValidationMessage> {
    val validationMessages = sentences.map { validateSentence(it) }.flatten().toMutableList()
    validateNoBrokenConsecutiveChains(sentences)?.let { validationMessages += it }

    if (bulkCalcValidation && featuresToggles.concurrentConsecutiveSentencesEnabled) {
      validateConsecutiveChainsForBulkCalculation(sentences)?.let { validationMessages += it }
    } else {
      validationMessages += validateConsecutiveChains(sentences)
    }

    return validationMessages
  }

  private data class ValidateConsecutiveSentenceUniqueRecord(
    val consecutiveToSequence: Int,
    val lineSequence: Int,
    val caseSequence: Int,
  )

  private fun validateConsecutiveChains(sentences: List<SentenceAndOffence>): List<ValidationMessage> = if (featuresToggles.concurrentConsecutiveSentencesEnabled) {
    validateConsecutiveSentenceUniqueWithDuration(sentences)
  } else {
    validateConsecutiveSentenceUnique(sentences)
  }

  private fun validateConsecutiveChainsForBulkCalculation(sentences: List<SentenceAndOffence>): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    if (validateConsecutiveSentenceUniqueWithDuration(sentences).isNotEmpty()) {
      validationMessages += ValidationMessage(ValidationCode.CONCURRENT_CONSECUTIVE_SENTENCES_NOTIFICATION)
    }
    validateConsecutiveChainForMultipleOffencesViolation(sentences)?.let { validationMessages += it }
    return validationMessages
  }

  private fun validateConsecutiveSentenceUnique(sentences: List<SentenceAndOffence>): List<ValidationMessage> {
    val consecutiveSentences = sentences.filter { it.consecutiveToSequence != null }
      .map { ValidateConsecutiveSentenceUniqueRecord(it.consecutiveToSequence!!, it.lineSequence, it.caseSequence) }
      .distinct()
    val sentencesGroupedByConsecutiveTo = consecutiveSentences.groupBy { it.consecutiveToSequence }
    return sentencesGroupedByConsecutiveTo.entries.filter { it.value.size > 1 }.map { entry ->
      val consecutiveToSentence = sentences.first { it.sentenceSequence == entry.key }
      ValidationMessage(
        ValidationCode.MULTIPLE_SENTENCES_CONSECUTIVE_TO,
        validationUtilities.getCaseSeqAndLineSeq(consecutiveToSentence),
      )
    }
  }

  private fun validateConsecutiveChainForMultipleOffencesViolation(sentences: List<SentenceAndOffence>): ValidationMessage? {
    val consecutiveSentences = sentences.filter { it.consecutiveToSequence != null }

    val hasDuplicateSequences = consecutiveSentences
      .groupingBy { it.sentenceSequence }
      .eachCount()
      .any { it.value > 1 }

    if (hasDuplicateSequences) {
      return ValidationMessage(ValidationCode.CONSECUTIVE_SENTENCE_WITH_MULTIPLE_OFFENCES)
    }

    val rootSequences = consecutiveSentences.mapNotNull { it.consecutiveToSequence }.toSet()

    val hasRootSentenceWithMultipleOffences = sentences
      .filter { it.consecutiveToSequence == null && it.sentenceSequence in rootSequences }
      .groupingBy { it.sentenceSequence }
      .eachCount()
      .any { it.value > 1 }

    return if (hasRootSentenceWithMultipleOffences) {
      ValidationMessage(ValidationCode.CONSECUTIVE_SENTENCE_WITH_MULTIPLE_OFFENCES)
    } else {
      null
    }
  }

  private fun validateConsecutiveSentenceUniqueWithDuration(sentences: List<SentenceAndOffence>): List<ValidationMessage> {
    val distinctSentences = sentences.distinctBy { it.sentenceSequence }
    val duplicateConsecutiveSequences = distinctSentences
      .map { it.consecutiveToSequence }
      .groupBy { it }
      .filter { it.value.size > 1 }
      .values
      .flatten()
      .takeIf { it.isNotEmpty() } ?: return emptyList()

    val chainsOfSentences =
      ConsecutiveSentenceUtil.createConsecutiveChains(distinctSentences, { it.sentenceSequence }, { it.consecutiveToSequence })

    val duplicateChains =
      chainsOfSentences.filter { chain ->
        chain.any { it.consecutiveToSequence != null && duplicateConsecutiveSequences.contains(it.consecutiveToSequence) }
      }.takeIf { it.isNotEmpty() } ?: return emptyList()

    val aggregatedDurations = duplicateChains.map { chain ->
      chain to chain.flatMap { sentence ->
        sentence.terms.map {
          Duration(
            mapOf(
              ChronoUnit.YEARS to it.years.toLong(),
              ChronoUnit.MONTHS to it.months.toLong(),
              ChronoUnit.WEEKS to it.weeks.toLong(),
              ChronoUnit.DAYS to it.days.toLong(),
            ),
          )
        }
      }.reduce { acc, duration ->
        acc.appendAll(duration.durationElements)
      }
    }
    val maximumDuration = aggregatedDurations.maxBy { duration ->
      val chain = duration.first
      val combinedDuration = duration.second
      val earliestSentenceDate = chain.minOf { it.sentenceDate }
      combinedDuration.getLengthInDays(earliestSentenceDate)
    }.second

    return listOf(
      ValidationMessage(
        ValidationCode.CONCURRENT_CONSECUTIVE_SENTENCES_DURATION,
        listOf(
          maximumDuration.durationElements[ChronoUnit.YEARS]?.toString() ?: "0",
          maximumDuration.durationElements[ChronoUnit.MONTHS]?.toString() ?: "0",
          maximumDuration.durationElements[ChronoUnit.WEEKS]?.toString() ?: "0",
          maximumDuration.durationElements[ChronoUnit.DAYS]?.toString() ?: "0",
        ),
      ),
    )
  }

  private fun validateSentence(it: SentenceAndOffence): List<ValidationMessage> = listOfNotNull(
    validateWithoutOffenceDate(it),
    validateOffenceDateAfterSentenceDate(it),
    validateOffenceRangeDateAfterSentenceDate(it),
  ) + validateDuration(it) + listOfNotNull(
    section91ValidationService.validate(it),
    edsValidationService.validate(it),
    sopcValidationService.validate(it).firstOrNull(),
    fineValidationService.validateFineAmount(it),
  )

  internal fun validateSentenceForManualEntry(it: SentenceAndOffence): List<ValidationMessage> = listOfNotNull(
    validateWithoutOffenceDate(it),
  )

  private fun validateWithoutOffenceDate(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    // It's valid to not have an end date for many offence types, but the start date must always be present in
    // either case. If an end date is null it will be set to the start date in the transformation.
    val invalid = sentencesAndOffence.offence.offenceStartDate == null
    if (invalid) {
      return ValidationMessage(ValidationCode.OFFENCE_MISSING_DATE, validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence))
    }
    return null
  }

  private fun validateOffenceDateAfterSentenceDate(
    sentencesAndOffence: SentenceAndOffence,
  ): ValidationMessage? {
    val offence = sentencesAndOffence.offence
    if (offence.offenceStartDate != null && offence.offenceStartDate > sentencesAndOffence.sentenceDate) {
      return ValidationMessage(ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE, validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence))
    }
    return null
  }

  private fun validateOffenceRangeDateAfterSentenceDate(sentencesAndOffence: SentenceAndOffence): ValidationMessage? {
    val offence = sentencesAndOffence.offence
    val invalid = offence.offenceEndDate != null && offence.offenceEndDate > sentencesAndOffence.sentenceDate
    if (invalid) {
      return ValidationMessage(ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE, validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence))
    }
    return null
  }

  private fun validateDuration(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)
    val allowedTermTypes = sentenceCalculationType.sentenceType?.supportedTerms ?: emptySet()

    return if (allowedTermTypes == setOf(TermType.IMPRISONMENT)) {
      validateSingleTermDuration(sentencesAndOffence)
    } else {
      validateImprisonmentAndLicenceTermDuration(sentencesAndOffence)
    }
  }

  private fun validateSingleTermDuration(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()
    val hasMultipleTerms = sentencesAndOffence.terms.size > 1
    if (hasMultipleTerms) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val emptyImprisonmentTerm =
        sentencesAndOffence.terms[0].days == 0 && sentencesAndOffence.terms[0].weeks == 0 && sentencesAndOffence.terms[0].months == 0 && sentencesAndOffence.terms[0].years == 0

      if (emptyImprisonmentTerm) {
        validationMessages.add(
          ValidationMessage(
            ValidationCode.ZERO_IMPRISONMENT_TERM,
            validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
          ),
        )
      }
    }
    return validationMessages
  }

  private fun validateImprisonmentAndLicenceTermDuration(sentencesAndOffence: SentenceAndOffence): List<ValidationMessage> {
    val validationMessages = mutableListOf<ValidationMessage>()

    val imprisonmentTerms = sentencesAndOffence.terms.filter { it.code == SentenceTerms.IMPRISONMENT_TERM_CODE }
    if (imprisonmentTerms.isEmpty()) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else if (imprisonmentTerms.size > 1) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val emptyTerm =
        imprisonmentTerms[0].days == 0 && imprisonmentTerms[0].weeks == 0 && imprisonmentTerms[0].months == 0 && imprisonmentTerms[0].years == 0
      if (emptyTerm) {
        validationMessages.add(
          ValidationMessage(
            ValidationCode.ZERO_IMPRISONMENT_TERM,
            validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
          ),
        )
      }
    }
    val licenceTerms = sentencesAndOffence.terms.filter { it.code == SentenceTerms.LICENCE_TERM_CODE }
    if (licenceTerms.isEmpty()) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else if (licenceTerms.size > 1) {
      validationMessages.add(
        ValidationMessage(
          ValidationCode.MORE_THAN_ONE_LICENCE_TERM,
          validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
        ),
      )
    } else {
      val sentenceCalculationType = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType)
      val typeClazz: Class<out AbstractSentence>? = sentenceCalculationType.sentenceType?.sentenceClazz
      if (typeClazz == ExtendedDeterminateSentence::class.java) {
        val duration =
          Period.of(
            licenceTerms[0].years,
            licenceTerms[0].months,
            licenceTerms[0].weeks * 7 + licenceTerms[0].days,
          )
        val endOfDuration = sentencesAndOffence.sentenceDate.plus(duration)
        val endOfOneYear = sentencesAndOffence.sentenceDate.plusYears(1)
        val endOfEightYears = sentencesAndOffence.sentenceDate.plusYears(8)

        if (endOfDuration.isBefore(endOfOneYear)) {
          validationMessages.add(
            ValidationMessage(
              ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR,
              validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        } else if (endOfDuration.isAfter(endOfEightYears)) {
          validationMessages.add(
            ValidationMessage(
              ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS,
              validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        }
      } else if (typeClazz == SopcSentence::class.java) {
        val duration =
          Period.of(
            licenceTerms[0].years,
            licenceTerms[0].months,
            licenceTerms[0].weeks * 7 + licenceTerms[0].days,
          )
        val endOfDuration = sentencesAndOffence.sentenceDate.plus(duration)
        val endOfOneYear = sentencesAndOffence.sentenceDate.plusYears(1)
        if (endOfDuration != endOfOneYear) {
          validationMessages.add(
            ValidationMessage(
              ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS,
              validationUtilities.getCaseSeqAndLineSeq(sentencesAndOffence),
            ),
          )
        }
      }
    }

    return validationMessages
  }

  internal fun validateSentenceHasNotBeenExtinguished(sentenceGroup: SentenceGroup): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val determinateSentences = sentenceGroup.sentences.filter { !it.isRecall() }
    if (determinateSentences.isNotEmpty()) {
      val earliestSentenceDate = determinateSentences.minOf { it.sentencedAt }
      val latestReleaseDateSentence = extractionService.mostRecentSentence(
        determinateSentences,
        SentenceCalculation::adjustedUncappedDeterminateReleaseDate,
      )
      if (earliestSentenceDate.minusDays(1)
          .isAfter(latestReleaseDateSentence.sentenceCalculation.adjustedUncappedDeterminateReleaseDate)
      ) {
        val hasRemand = latestReleaseDateSentence.sentenceCalculation.adjustments.remand != 0L
        val hasTaggedBail =
          latestReleaseDateSentence.sentenceCalculation.adjustments.taggedBail != 0L
        if (hasRemand) messages += ValidationMessage(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND)

        if (hasTaggedBail) messages += ValidationMessage(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL)
      }
    }
    return messages
  }

  fun validateNoBrokenConsecutiveChains(sentences: List<SentenceAndOffence>): ValidationMessage? {
    val sentenceSequences = mutableSetOf<Int>()
    val consecutiveToSequences = mutableSetOf<Int>()

    for (sentence in sentences) {
      sentenceSequences += sentence.sentenceSequence
      sentence.consecutiveToSequence?.let { consecutiveToSequences += it }
    }

    if (consecutiveToSequences.isEmpty()) return null

    val hasBrokenChain = consecutiveToSequences.any { it !in sentenceSequences }

    return if (hasBrokenChain) {
      ValidationMessage(ValidationCode.BROKEN_CONSECUTIVE_CHAINS)
    } else {
      null
    }
  }
}
