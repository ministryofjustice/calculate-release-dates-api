package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceAdjustedCalculationService
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TimelineCalculator(
  private val sentenceAdjustedCalculationService: SentenceAdjustedCalculationService,
  private val bookingExtractionService: BookingExtractionService,
) {

  fun getLatestCalculation(sentences: List<List<CalculableSentence>>, offender: Offender, returnToCustodyDate: LocalDate? = null): CalculationResult {
    calculateUnusedAdas(sentences)
    sentences.flatten().forEach {
      sentenceAdjustedCalculationService.calculateDatesFromAdjustments(it, offender)
    }
    val adjustAgain = calculateUnusedLicenseAdas(sentences)
    if (adjustAgain) {
      sentences.flatten().forEach {
        sentenceAdjustedCalculationService.calculateDatesFromAdjustments(it, offender)
      }
    }
    return bookingExtractionService.extract(
      sentences.flatten(),
      sentences,
      offender,
      returnToCustodyDate,
    )
  }

  fun calculateUnusedLicenseAdas(sentenceGroups: List<List<CalculableSentence>>): Boolean {
    var anyUnusedLicenseAads = false
    sentenceGroups.forEach { group ->
      val expiry = group.maxOfOrNull { it.sentenceCalculation.expiryDate }
      if (expiry == null) {
        return@forEach
      }
      val unusedLicenseDays = group.filter {
        val ledBreakdown = it.sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.LED]
        ledBreakdown != null &&
          ledBreakdown.rules.contains(CalculationRule.LED_CONSEC_ORA_AND_NON_ORA) &&
          it.sentenceCalculation.licenceExpiryDate!!.isAfter(
            expiry,
          )
      }.maxOfOrNull {
        ChronoUnit.DAYS.between(expiry, it.sentenceCalculation.licenceExpiryDate!!)
      }

      if (unusedLicenseDays != null && unusedLicenseDays > 0) {
        setAdjustments(
          group,
          SentenceAdjustments(
            unusedLicenceAdaDays = unusedLicenseDays,
          ),
        )
        anyUnusedLicenseAads = true
      }
    }
    return anyUnusedLicenseAads
  }

  fun calculateUnusedAdas(sentenceGroups: List<List<CalculableSentence>>) {
    sentenceGroups.forEach { group ->
      group.forEach {
        val expiry = it.sentenceCalculation.expiryDate
        if (it.sentenceCalculation.releaseDate.isAfter(expiry)) {
          setAdjustments(
            listOf(it),
            SentenceAdjustments(
              unusedAdaDays = ChronoUnit.DAYS.between(expiry, it.sentenceCalculation.releaseDate),
            ),
          )
        }
      }
    }
  }

  fun setAdjustments(sentences: List<CalculableSentence>, sentenceAdjustment: SentenceAdjustments) {
    sentences.forEach { sentence ->
      var adjustments = sentenceAdjustment

      if (sentence.sentenceParts().all { it is Term }) {
        adjustments = adjustments.copy(
          awardedDuringCustody = 0,
        )
      }

      if (sentence is AFineSentence && sentence.offence.isCivilOffence()) {
        adjustments = adjustments.copy(
          remand = 0,
          taggedBail = 0,
        )
      } else if (sentence.isOrExclusivelyBotus()) {
        adjustments = adjustments.copy(
          remand = 0,
          taggedBail = 0,
        )
      } else if (sentence.isDto() && sentence.identificationTrack == SentenceIdentificationTrack.DTO_BEFORE_PCSC) {
        adjustments = adjustments.copy(
          remand = 0,
        )
      }

      adjustments = if (sentence.isRecall()) {
        adjustments.copy(
          remand = 0,
          taggedBail = 0,
        )
      } else {
        adjustments.copy(
          recallRemand = 0,
          recallTaggedBail = 0,
        )
      }

      val sentenceCalculation = sentence.sentenceCalculation
      sentenceCalculation.adjustments = sentenceCalculation.adjustments.copy(
        remand = sentenceCalculation.adjustments.remand + adjustments.remand,
        taggedBail = sentenceCalculation.adjustments.taggedBail + adjustments.taggedBail,
        recallRemand = sentenceCalculation.adjustments.recallRemand + adjustments.recallRemand,
        recallTaggedBail = sentenceCalculation.adjustments.recallTaggedBail + adjustments.recallTaggedBail,
        ualDuringCustody = sentenceCalculation.adjustments.ualDuringCustody + adjustments.ualDuringCustody,
        awardedDuringCustody = sentenceCalculation.adjustments.awardedDuringCustody + adjustments.awardedDuringCustody,

        awardedAfterDeterminateRelease = sentenceCalculation.adjustments.awardedAfterDeterminateRelease + adjustments.awardedAfterDeterminateRelease,
        ualAfterDeterminateRelease = sentenceCalculation.adjustments.ualAfterDeterminateRelease + adjustments.ualAfterDeterminateRelease,
        ualAfterFtr = sentenceCalculation.adjustments.ualAfterFtr + adjustments.ualAfterFtr,

        servedAdaDays = sentenceCalculation.adjustments.servedAdaDays + adjustments.servedAdaDays,
        unusedAdaDays = sentenceCalculation.adjustments.unusedAdaDays + adjustments.unusedAdaDays,
        unusedLicenceAdaDays = sentenceCalculation.adjustments.unusedLicenceAdaDays + adjustments.unusedLicenceAdaDays,
      )
    }
  }
}
