package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.UUID

data class Sentence(
  val duration: Duration,
  val sentencedAt: LocalDate,
  val remandInDays: Int,
  val taggedBailInDays: Int
) {

  val identifier: UUID = UUID.randomUUID()
  val concurrentSentences = mutableListOf<Sentence>()
}
