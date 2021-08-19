package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceTerm
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.TestData as EntityTestData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.TestData as ModelTestData

/*
** Functions which transform entities objects into their model equivalents.
** Sometimes a pass-thru but very useful when objects need to be altered or enriched
*/

fun transform(testData: EntityTestData): ModelTestData {
  return ModelTestData(
    key = testData.key,
    value = testData.value
  )
}

fun transform(sentenceTerm: SentenceTerm): Sentence {
  val offence = Offence(startedAt = sentenceTerm.startDate)
  val duration = Duration()
  duration.append(sentenceTerm.days, DAYS)
  duration.append(sentenceTerm.months, MONTHS)
  duration.append(sentenceTerm.years, YEARS)
  val consecutiveSentenceUUIDs = if (sentenceTerm.consecutiveTo != null)
    mutableListOf(
      generateUUIDForSentence(sentenceTerm.bookingId, sentenceTerm.consecutiveTo)
    )
  else
    mutableListOf()

  return Sentence(
    sentencedAt = sentenceTerm.sentenceStartDate,
    duration = duration,
    offence = offence,
    identifier = generateUUIDForSentence(sentenceTerm.bookingId, sentenceTerm.sentenceSequence),
    consecutiveSentenceUUIDs = consecutiveSentenceUUIDs
  )
}

private fun generateUUIDForSentence(bookingId: Long, sequence: Int) =
  UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray())

fun transform(prisonerDetails: PrisonerDetails): Offender {
  return Offender(
    name = prisonerDetails.firstName + ' ' + prisonerDetails.lastName,
    dateOfBirth = prisonerDetails.dateOfBirth,
    reference = prisonerDetails.offenderNo,
  )
}

fun transform(sentenceAdjustments: SentenceAdjustments): MutableMap<AdjustmentType, Int> {
  val adjustments = mutableMapOf<AdjustmentType, Int>()
  adjustments[REMAND] = sentenceAdjustments.remand
  adjustments[TAGGED_BAIL] = sentenceAdjustments.taggedBail
  adjustments[UNLAWFULLY_AT_LARGE] = sentenceAdjustments.unlawfullyAtLarge
  return adjustments
}
