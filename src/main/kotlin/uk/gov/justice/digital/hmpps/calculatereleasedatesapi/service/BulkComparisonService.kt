package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Mismatch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository

@Service
class BulkComparisonService(
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val prisonService: PrisonService,
  private val calculationTransactionalService: CalculationTransactionalService,
) {

  fun populate(comparison: Comparison): List<CalculableSentenceEnvelope> {
    return getPeopleAtEstablishment(comparison)
  }

  fun recordMismatchesForComparison(comparisonToCreate: Comparison, mismatches: List<Mismatch>) {
    // TODO is mismatch stored in the comparison person table? columns need adding?
    comparisonPersonRepository
  }

  fun determineIfMismatch(calculableSentenceEnvelope: CalculableSentenceEnvelope): Mismatch {
    // Specify the default
    val mismatch = Mismatch(
      isMatch = false,
      isValid = false,
      calculableSentenceEnvelope = calculableSentenceEnvelope,
      calculatedReleaseDates = null,
    )

    val calculationUserInput = CalculationUserInputs(
      listOf(),
      calculableSentenceEnvelope.sentenceCalcDates?.earlyRemovalSchemeEligibilityDate != null,
      true,
    )

    val prisonApiSourceData: PrisonApiSourceData = this.convert(calculableSentenceEnvelope)

    val validationResult = calculationTransactionalService.validateAndCalculate(
      calculableSentenceEnvelope.person.prisonerNumber,
      calculationUserInput,
      false,
      prisonApiSourceData,
    )

    mismatch.isValid = validationResult.messages.isEmpty()
    mismatch.calculatedReleaseDates = validationResult.calculatedReleaseDates

    if (mismatch.isValid) {
      mismatch.isMatch =
        identifyMismatches(validationResult.calculatedReleaseDates, calculableSentenceEnvelope.sentenceCalcDates)
    } else {
      mismatch.isMatch = false
    }

    // returns a mismatch object
    return mismatch
  }

  private fun identifyMismatches(
    calculatedReleaseDates: CalculatedReleaseDates?,
    sentenceCalcDates: SentenceCalcDates?,
  ): Boolean {
    if (calculatedReleaseDates != null && calculatedReleaseDates.dates.isNotEmpty()) {
      val calculatedSentenceCalcDates = calculatedReleaseDates.toSentenceCalcDates()
      if (sentenceCalcDates != null) {
        return calculatedSentenceCalcDates == sentenceCalcDates
      }
    }
    return true
  }

  @Async
  fun getPeopleAtEstablishment(comparison: Comparison): List<CalculableSentenceEnvelope> {
    if (!comparison.manualInput && comparison.prison != null) {
      val activeBookingsAtEstablishment = prisonService.getActiveBookingsByEstablishment(comparison.prison)
      val comparisonPeople = activeBookingsAtEstablishment.map {
        ComparisonPerson(
          comparisonId = comparison.id,
          person = it.person.prisonerNumber,
          latestBookingId = it.latestPrisonTerm!!.bookingId!!,
        )
      }
      // record all the people we are going to run comparison for
      comparisonPersonRepository.saveAll(comparisonPeople)
      return activeBookingsAtEstablishment
    }
    return emptyList()
  }

  private fun convert(source: CalculableSentenceEnvelope): PrisonApiSourceData {
    val prisonerDetails = PrisonerDetails(
      bookingId = source.latestPrisonTerm?.bookingId!!,
      offenderNo = source.person.prisonerNumber,
      dateOfBirth = source.person.dateOfBirth,
    )

    val sentencesOffences = ArrayList<SentenceAndOffences>()

    source.latestPrisonTerm.courtSentences!!.forEach { courtCase ->
      for (sentence in courtCase.sentences!!) {
        val offenderOffences: List<OffenderOffence> =
          sentence.offences?.map {
            OffenderOffence(
              it.offenderChargeId!!,
              it.offenceStartDate,
              it.offenceEndDate,
              it.offenceCode!!,
              it.offenceDescription!!,
              it.indicators ?: emptyList(),
            )
          }?.toList() ?: emptyList()

        sentencesOffences.add(
          SentenceAndOffences(
            source.latestPrisonTerm.bookingId,
            sentence.sentenceSequence!!,
            sentence.lineSeq!!.toInt(),
            courtCase.caseSeq!!,
            sentence.consecutiveToSequence,
            sentence.sentenceStatus!!,
            sentence.sentenceCategory!!,
            sentence.sentenceCalculationType!!,
            sentence.sentenceTypeDescription!!,
            sentence.sentenceStartDate!!,
            sentence.terms?.map { terms ->
              SentenceTerms(
                terms.years ?: 0,
                terms.months ?: 0,
                terms.weeks ?: 0,
                terms.days ?: 0,
              )
            } ?: emptyList(),
            offenderOffences,
            courtCase.caseInfoNumber,
            courtCase.court?.description,
            sentence.fineAmount?.toBigDecimal(),
          ),
        )
      }
    }

    val bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(
      source.bookingAdjustments.map {
        BookingAdjustment(
          it.active,
          it.fromDate,
          it.toDate,
          it.numberOfDays,
          it.type,
        )
      }.toList(),
      source.sentenceAdjustments.map {
        SentenceAdjustment(
          it.sentenceSequence!!,
          it.active,
          it.fromDate,
          it.toDate,
          it.numberOfDays!!,
          it.type!!,
        )
      }.toList(),
    )

    val offenderFinePayments = source.offenderFinePayments.map {
      OffenderFinePayment(
        it.bookingId!!,
        it.paymentDate!!,
        it.paymentAmount!!,
      )
    }

    val fixedTermRecallDetails = source.fixedTermRecallDetails?.let {
      FixedTermRecallDetails(
        it.bookingId!!,
        it.returnToCustodyDate!!,
        it.recallLength!!,
      )
    }

    val returnToCustodyDate = source.fixedTermRecallDetails?.let {
      ReturnToCustodyDate(
        it.bookingId!!,
        it.returnToCustodyDate!!,
      )
    }

    return PrisonApiSourceData(
      sentencesOffences,
      prisonerDetails,
      bookingAndSentenceAdjustments,
      offenderFinePayments,
      returnToCustodyDate,
      fixedTermRecallDetails,
    )
  }
}
