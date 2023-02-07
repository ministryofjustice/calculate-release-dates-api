package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

interface SingleTermed : CalculableSentence {
  val standardSentences: List<AbstractSentence>
}