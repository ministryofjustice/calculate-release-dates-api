package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.SLED
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Sentence(
  val offence: Offence,
  override val duration: Duration,
  override val sentencedAt: LocalDate,
  var identifier: UUID = UUID.randomUUID(),
  // Sentence UUIDS that this sentence is consecutive to.
  var consecutiveSentenceUUIDs: MutableList<UUID> = mutableListOf(),
  val sequence: Int? = null,
  var sentenceParts: List<Sentence> = listOf()
) : SentenceTimeline {
  @JsonIgnore
  lateinit var consecutiveSentences: MutableList<Sentence>

  @JsonIgnore
  lateinit var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  lateinit var sentenceTypes: List<SentenceType>

  fun isSentenceCalculated(): Boolean {
    return this::sentenceCalculation.isInitialized
  }

  fun associateSentences(sentences: List<Sentence>) {
    consecutiveSentences = mutableListOf()
    sentences.forEach { sentence ->
      this.consecutiveSentenceUUIDs.forEach {
        if (sentence.identifier == it) {
          consecutiveSentences.add(sentence)
        }
      }
    }
  }

  @JsonIgnore
  fun getReleaseDateType(): SentenceType {
    return if (sentenceTypes.contains(PED))
      PED else if (sentenceCalculation.isReleaseDateConditional)
      CRD else
      ARD
  }

  fun buildString(): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val expiryDateType = if (sentenceTypes.contains(SLED)) "SLED" else "SED"
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
      "Total number of days of added (UAL) \t:\t${sentenceCalculation.calculatedTotalAddedDays}\n" +
      "Total number of days of awarded (ADA / RADA) \t:\t${sentenceCalculation.calculatedTotalAwardedDays}\n" +

      "Total number of days to Licence Expiry Date (LED)\t:\t${sentenceCalculation.numberOfDaysToLicenceExpiryDate}\n" +
      "LED\t:\t${sentenceCalculation.licenceExpiryDate?.format(formatter)}\n" +

      "Number of days to Non Parole Date (NPD)\t:\t${sentenceCalculation.numberOfDaysToNonParoleDate}\n" +
      "Non Parole Date (NPD)\t:\t${sentenceCalculation.nonParoleDate?.format(formatter)}\n" +

      "Number of days to Home Detention Curfew Expiry Date (HDCED)\t:\t" +
      "${sentenceCalculation.numberOfDaysToHomeDetentionCurfewExpiryDate}\n" +
      "Home Detention Curfew Expiry Date (HDCED)\t:\t" +
      "${sentenceCalculation.homeDetentionCurfewExpiryDateDate?.format(formatter)}\n" +

      "Effective $expiryDateType\t:\t${sentenceCalculation.expiryDate?.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${sentenceCalculation.releaseDate?.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${sentenceCalculation.topUpSupervisionDate?.format(formatter)}\n"
  }

  fun deepCopy(): Sentence {
    return this.copy(consecutiveSentenceUUIDs = this.consecutiveSentenceUUIDs.toMutableList())
  }
}
