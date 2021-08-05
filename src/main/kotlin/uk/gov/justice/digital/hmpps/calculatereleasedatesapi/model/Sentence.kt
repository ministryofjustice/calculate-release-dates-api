package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.threeten.extra.LocalDateRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToLong

data class Sentence(
  val offence: Offence,
  val duration: Duration,
  val sentencedAt: LocalDate,
  val remandInDays: Int,
  val taggedBailInDays: Int,
  val unlawfullyAtLargeInDays: Int = 0,
  var identifier: UUID = UUID.randomUUID(),
) {

  lateinit var sentenceCalculation: SentenceCalculation
  lateinit var sentenceTypes: List<SentenceType>

  var concurrentSentences = mutableListOf<Sentence>()

  fun durationIsGreaterThan(length: Long, period: ChronoUnit): Boolean {
    return (
      duration.getLengthInDays(this.sentencedAt) >
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun durationIsGreaterThanOrEqualTo(length: Long, period: ChronoUnit): Boolean {
    return (
      duration.getLengthInDays(this.sentencedAt) >=
        ChronoUnit.DAYS.between(this.sentencedAt, this.sentencedAt.plus(length, period))
      )
  }

  fun getHalfSentenceDate(): LocalDate {
    val days = (duration.getLengthInDays(this.sentencedAt).toDouble() / 2).roundToLong()
    return this.sentencedAt.plusDays(days)
  }

  fun getDateRange(): LocalDateRange? {
    return LocalDateRange.of(sentencedAt, duration.getEndDate(sentencedAt))
  }

  fun buildString(): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val expiryDateType = if (sentenceTypes.contains(SentenceType.SLED)) "SLED" else "SED"
    val releaseDateType = if (sentenceCalculation.isReleaseDateConditional) "CRD" else "ARD"

    return "Sentence\t:\t\n" +
      "Duration\t:\t$duration\n" +
      "${duration.toPeriodString(sentencedAt)}\n" +
      "Sentence Types\t:\t$sentenceTypes\n" +
      "Number of Days in Sentence\t:\t${duration.getLengthInDays(sentencedAt)}\n" +
      "Date of $expiryDateType\t:\t${sentenceCalculation.unadjustedExpiryDate.format(formatter)}\n" +
      "Number of days to $releaseDateType\t:\t${sentenceCalculation.numberOfDaysToReleaseDate}\n" +
      "Date of $releaseDateType\t:\t${sentenceCalculation.unadjustedReleaseDate.format(formatter)}\n" +
      "Total number of days of remand / tagged bail time / UAL\t:\t${sentenceCalculation.calculatedTotalRemandDays}\n" +
      "LED\t:\t${sentenceCalculation.licenceExpiryDate?.format(formatter)}\n" +
      "Effective $expiryDateType\t:\t${sentenceCalculation.expiryDate?.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${sentenceCalculation.releaseDate?.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${sentenceCalculation.topUpSupervisionDate?.format(formatter)}\n"
  }
}
