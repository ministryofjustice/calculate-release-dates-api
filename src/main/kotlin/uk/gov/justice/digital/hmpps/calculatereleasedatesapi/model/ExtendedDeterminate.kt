package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

interface ExtendedDeterminate : IdentifiableSentence, CalculableSentence, ExtractableSentence {
  val automaticRelease: Boolean

  fun getCustodialLengthInDays(): Int
}
