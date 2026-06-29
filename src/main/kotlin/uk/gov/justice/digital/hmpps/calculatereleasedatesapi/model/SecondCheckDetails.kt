package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDateTime

data class SecondCheckDetails(val checkedByUsername: String?, val checkedByDisplayName: String?, val checkedAt: LocalDateTime?)
