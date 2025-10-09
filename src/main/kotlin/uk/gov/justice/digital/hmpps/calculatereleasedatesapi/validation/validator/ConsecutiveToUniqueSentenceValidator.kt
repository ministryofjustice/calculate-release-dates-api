package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.ConsecutiveSentenceUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import java.time.temporal.ChronoUnit

@Component
class ConsecutiveToUniqueSentenceValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val distinctSentences = sourceData.sentenceAndOffences.distinctBy { it.sentenceSequence }
    val duplicateConsecutiveSequences = distinctSentences
      .map { it.consecutiveToSequence }
      .groupBy { it }
      .filter { it.value.size > 1 }
      .values
      .flatten()
      .takeIf { it.isNotEmpty() } ?: return emptyList()

    val chainsOfSentences =
      ConsecutiveSentenceUtil.createConsecutiveChains(
        distinctSentences,
        { it.sentenceSequence },
        { it.consecutiveToSequence },
      )

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

  override fun validationOrder() = ValidationOrder.WARNING
}
