package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.util.EnumSet

@Service
class OffenceSdsPlusLookupService(
  private val manageOffencesService: ManageOffencesService,
) {

  /**
   * Queries MO against Sentences of type and duration that could fit the SDS+ criteria.
   * Sets the [SentenceAndOffenceWithReleaseArrangements.isSDSPlus] indicator if it falls under SDS+.
   *
   * @param sentencesAndOffences - A list of [SentenceAndOffence] to process for SDS+
   * @return matchingSentenceMap - A list of [SentenceAndOffenceWithReleaseArrangements] with SDS+ markers set
   */
  fun populateSdsPlusMarkerForOffences(sentencesAndOffences: List<SentenceAndOffence>): List<SentenceAndOffenceWithReleaseArrangements> {
    log.info("Checking ${sentencesAndOffences.size} sentences for SDS+")
    val offencesToCheck = getMatchingSentenceOffenceCodes(sentencesAndOffences)
    val moCheckResponses = if (offencesToCheck.isNotEmpty()) manageOffencesService.getPcscMarkersForOffenceCodes(*offencesToCheck.toTypedArray()).associateBy { it.offenceCode } else emptyMap()
    return sentencesAndOffences.map { sentencesAndOffence ->
      if (sentencesAndOffence.offence.offenceCode in offencesToCheck && checkIsSDSPlus(sentencesAndOffence, moCheckResponses)) {
        SentenceAndOffenceWithReleaseArrangements(sentencesAndOffence, true)
      } else {
        SentenceAndOffenceWithReleaseArrangements(sentencesAndOffence, false)
      }
    }
  }

  private fun checkIsSDSPlus(
    sentenceAndOffence: SentenceAndOffence,
    moCheckResponses: Map<String, OffencePcscMarkers>,
  ): Boolean {
    val moResponseForOffence = moCheckResponses[sentenceAndOffence.offence.offenceCode]
    val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffence)
    val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)
    val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffence)
    var sdsPlusIdentified = false
    if (sentenceCalculationType in SDS_AND_DYOI_POST_PCSC_CALC_TYPES) {
      if (sentencedWithinOriginalSdsPlusWindow(sentenceAndOffence) && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListA == true) {
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListD == true) {
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && fourToUnderSeven(sentenceAndOffence) && moResponseForOffence?.pcscMarkers?.inListB == true) {
        sdsPlusIdentified = true
      }
    } else if (sentenceCalculationType in S250_POST_PCSC_CALC_TYPES) {
      if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListC == true) {
        sdsPlusIdentified = true
      }
    }
    return sdsPlusIdentified
  }

  private fun getMatchingSentenceOffenceCodes(sentencesAndOffences: List<SentenceAndOffence>): List<String> {
    return sentencesAndOffences
      .filter { SentenceCalculationType.isSupported(it.sentenceCalculationType) }
      .filter { sentenceAndOffences ->
        val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffences.sentenceCalculationType)
        val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffences)
        val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffences)
        val sentencedWithinOriginalSdsWindow = sentencedWithinOriginalSdsPlusWindow(sentenceAndOffences)

        var matchFilter = false

        if (sentenceCalculationType in SDS_AND_DYOI_POST_PCSC_CALC_TYPES) {
          if (sentencedWithinOriginalSdsWindow && sevenYearsOrMore) {
            matchFilter = true
          } else if (sentenceIsAfterPcsc && overFourYearsSentenceLength(sentenceAndOffences)) {
            matchFilter = true
          }
        } else if (sentenceCalculationType in S250_POST_PCSC_CALC_TYPES && sentenceIsAfterPcsc && sevenYearsOrMore) {
          matchFilter = true
        }
        matchFilter
      }.map { it.offence.offenceCode }
  }

  private fun sentencedAfterPcsc(sentence: SentenceAndOffence): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(
      ImportantDates.PCSC_COMMENCEMENT_DATE,
    )
  }

  private fun sentencedWithinOriginalSdsPlusWindow(sentence: SentenceAndOffence): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) && !sentencedAfterPcsc(sentence)
  }

  private fun overFourYearsSentenceLength(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears)
  }

  private fun endOfSentence(sentence: SentenceAndOffence): LocalDate {
    val duration =
      Period.of(sentence.terms[0].years, sentence.terms[0].months, sentence.terms[0].weeks * 7 + sentence.terms[0].days)
    return sentence.sentenceDate.plus(duration)
  }

  private fun sevenYearsOrMore(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfSevenYears)
  }

  private fun fourToUnderSeven(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears) && endOfSentence.isBefore(endOfSevenYears)
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val SDS_AND_DYOI_POST_PCSC_CALC_TYPES = EnumSet.of(
      SentenceCalculationType.ADIMP,
      SentenceCalculationType.ADIMP_ORA,
      SentenceCalculationType.YOI,
      SentenceCalculationType.YOI_ORA,
    )
    private val S250_POST_PCSC_CALC_TYPES = EnumSet.of(
      SentenceCalculationType.SEC250,
      SentenceCalculationType.SEC250_ORA,
    )
  }
}
