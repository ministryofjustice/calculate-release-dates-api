package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToLong

data class Sentence(
  override val offence: Offence,
  val duration: Duration,
  override val sentencedAt: LocalDate,
  val identifier: UUID = UUID.randomUUID(),
  // Sentence UUIDS that this sentence is consecutive to.
  val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  val caseSequence: Int? = null,
  val lineSequence: Int? = null
) : IdentifiableSentence, CalculableSentence, ExtractableSentence {
  @JsonIgnore
  @Transient
  override lateinit var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  @Transient
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  @Transient
  override lateinit var releaseDateTypes: List<ReleaseDateType>

  @JsonIgnore
  fun isSentenceCalculated(): Boolean {
    return this::sentenceCalculation.isInitialized
  }

  fun buildString(): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val expiryDateType = if (releaseDateTypes.contains(SLED)) "SLED" else "SED"
    val releaseDateType = getReleaseDateType().toString()

    return "Sentence\t:\t\n" +
      "Duration\t:\t$duration\n" +
      "${duration.toPeriodString(sentencedAt)}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
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

  override fun getLengthInDays(): Int {
    return duration.getLengthInDays(this.sentencedAt)
  }
}
