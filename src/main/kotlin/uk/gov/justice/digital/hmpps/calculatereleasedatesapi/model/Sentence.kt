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
  var identifier: UUID = UUID.randomUUID(),
  var consecutiveSentenceUUIDs: MutableList<UUID> = mutableListOf()
) {
  lateinit var consecutiveSentences: MutableList<Sentence>
  lateinit var sentenceCalculation: SentenceCalculation

  fun isSentenceCalculated(): Boolean {
    return this::sentenceCalculation.isInitialized
  }

  lateinit var sentenceTypes: List<SentenceType>

  fun associateSentences(sentences: List<Sentence>) {
    consecutiveSentences = mutableListOf()
    sentences.forEach {
      sentence ->
      this.consecutiveSentenceUUIDs.forEach {
        if (sentence.identifier == it) { consecutiveSentences.add(sentence) }
      }
    }
  }

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

  fun getReleaseDateType(): SentenceType {
    return if (sentenceTypes.contains(SentenceType.PED))
      SentenceType.PED else if (sentenceCalculation.isReleaseDateConditional)
      SentenceType.CRD else
      SentenceType.ARD
  }

  fun buildString(): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val expiryDateType = if (sentenceTypes.contains(SentenceType.SLED)) "SLED" else "SED"
    val releaseDateType = getReleaseDateType().toString()

    return "Sentence\t:\t\n" +
      "Duration\t:\t$duration\n" +
      "${duration.toPeriodString(sentencedAt)}\n" +
      "Sentence Types\t:\t$sentenceTypes\n" +
      "Number of Days in Sentence\t:\t${duration.getLengthInDays(sentencedAt)}\n" +
      "Date of $expiryDateType\t:\t${sentenceCalculation.unadjustedExpiryDate.format(formatter)}\n" +
      "Number of days to $releaseDateType\t:\t${sentenceCalculation.numberOfDaysToReleaseDate}\n" +
      "Date of $releaseDateType\t:\t${sentenceCalculation.unadjustedReleaseDate.format(formatter)}\n" +
      "Total number of days of deducted (remand / tagged bail)\t:" +
      "\t${sentenceCalculation.calculatedTotalDeductedDays}\n" +
      "Total number of days of added (ADA) \t:\t${sentenceCalculation.calculatedTotalAddedDays}\n" +

      "Total number of days to Licence Expiry Date (LED)\t:\t${sentenceCalculation.numberOfDaysToLicenceExpiryDate}\n" +
      "LED\t:\t${sentenceCalculation.licenceExpiryDate?.format(formatter)}\n" +

      "Number of days to Non Parole Date (NPD)\t:\t${sentenceCalculation.numberOfDaysToNonParoleDate}\n" +
      "Non Parole Date (NPD)\t:\t${sentenceCalculation.nonParoleDate?.format(formatter)}\n" +

      "Effective $expiryDateType\t:\t${sentenceCalculation.expiryDate?.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${sentenceCalculation.releaseDate?.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${sentenceCalculation.topUpSupervisionDate?.format(formatter)}\n"
  }

  fun canMergeWith(secondSentence: Sentence): Boolean {
    return (
      this.sentenceTypes.containsAll(secondSentence.sentenceTypes) ||
        this.containsSimilar(secondSentence)
      )
  }

  private fun containsSimilar(sentence: Sentence): Boolean {
    return (
      this.sentenceTypes.containsAll(listOf(SentenceType.SLED, SentenceType.CRD)) &&
        sentence.sentenceTypes.containsAll(listOf(SentenceType.SED, SentenceType.ARD)) ||
        sentence.sentenceTypes.containsAll(listOf(SentenceType.SLED, SentenceType.CRD)) &&
        this.sentenceTypes.containsAll(listOf(SentenceType.SED, SentenceType.ARD))
      )
  }
}
