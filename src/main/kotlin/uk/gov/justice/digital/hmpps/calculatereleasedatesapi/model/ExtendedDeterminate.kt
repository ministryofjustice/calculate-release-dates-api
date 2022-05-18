package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate

interface ExtendedDeterminate: IdentifiableSentence, CalculableSentence, ExtractableSentence {
  val automaticRelease: Boolean

  fun getCustodialLengthInDays(): Int
}

