package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import java.time.Instant

data class ComparisonToDelete(val id: Int, val numberOfComparisons: Long, val calculatedAt: Instant)
