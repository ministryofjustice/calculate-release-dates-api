package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

class ExtendedStandardConsecutiveSentence(orderedStandardSentences: List<ExtendedDeterminateSentence>) : AbstractConsecutiveSentence<ExtendedDeterminateSentence>(
  orderedStandardSentences
) {

  override fun buildString(): String {
    return "ExtendedDeterminateSentence\t:\t\n" +
      "Number of sentences\t:\t${orderedStandardSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun getLengthInDays(): Int {
    return 0
  }
}
