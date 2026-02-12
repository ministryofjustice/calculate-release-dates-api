package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

data class AllExpectedSentenceLevelDates(val sentenceLevelDates: List<ExpectedSentenceLevelDates>)
data class ExpectedSentenceLevelDates(val identifier: String, val dates: Map<ReleaseDateType, LocalDate>, val impactsFinalReleaseDate: Boolean)
