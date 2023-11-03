package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.util.*

@Service
class OffenceSdsPlusLookupService(
        private val manageOffencesService: ManageOffencesService, private val prisonService: PrisonService
) {
    val postPcscCalcTypes: Map<String, EnumSet<SentenceCalculationType>> = mapOf(
            "SDS+" to EnumSet.of(
                    SentenceCalculationType.ADIMP,
                    SentenceCalculationType.ADIMP_ORA,
                    SentenceCalculationType.SEC250,
                    SentenceCalculationType.SEC250_ORA,
                    SentenceCalculationType.SEC91_03,
                    SentenceCalculationType.SEC91_03_ORA,
                    SentenceCalculationType.YOI,
                    SentenceCalculationType.YOI_ORA,
            ),
    )

    fun setSdsPlusMarkerForOffences(prisonerId: String): List<String> {
        val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
        val sentencesAndOffences = prisonService.getSentencesAndOffences(prisonerDetails.bookingId)

        val bookingIdToSentences = getMatchingSentencesToBookingId(sentencesAndOffences)
        val offencesToCheck = getOffenceCodesToCheckWithMO(bookingIdToSentences)

        return offencesToCheck;
    }

    fun getOffenceCodesToCheckWithMO(bookingIdToSentences: Map<Long, List<SentenceAndOffences>>): List<String> {
        val offencesToCheck = bookingIdToSentences.map {
            it.value.flatMap { offenceList -> offenceList.offences.map { offence -> offence.offenceCode } }
        }.flatten().distinct();
        return offencesToCheck
    }

    fun getMatchingSentencesToBookingId(sentencesAndOffences: List<SentenceAndOffences>): Map<Long, List<SentenceAndOffences>> {
        val bookingIdToSentences = sentencesAndOffences
                .filter { SentenceCalculationType.isSupported(it.sentenceCalculationType) }
                .filter { sentenceAndOffences ->
                    val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffences.sentenceCalculationType)
                    val sentencedAfterPcsc = sentencedAfterPcsc(sentenceAndOffences)

                    if (sentencedAfterPcsc) {
                        val matchingSentenceType = postPcscCalcTypes["SDS+"]!!.contains(sentenceCalculationType)
                        if (matchingSentenceType && overFourYearsSentenceLength(sentenceAndOffences)) {
                            return@filter true;
                        }
                    }
                    return@filter false;
                }
                .groupBy { it.bookingId }
        return bookingIdToSentences
    }

    private fun sentencedAfterPcsc(sentence: SentenceAndOffences): Boolean {
        return sentence.sentenceDate.isAfterOrEqualTo(
                ImportantDates.SDS_PLUS_COMMENCEMENT_DATE,
        )
    }
    private fun overFourYearsSentenceLength(sentence: SentenceAndOffences): Boolean {
        val endOfSentence = endOfSentence(sentence)
        val endOfFourYears = sentence.sentenceDate.plusYears(4)
        return endOfSentence.isAfterOrEqualTo(endOfFourYears)
    }

    private fun endOfSentence(sentence: SentenceAndOffences): LocalDate {
        val duration =
                Period.of(sentence.terms[0].years, sentence.terms[0].months, sentence.terms[0].weeks * 7 + sentence.terms[0].days)
        return sentence.sentenceDate.plus(duration)
    }
}