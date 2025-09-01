package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

class NomisResourceLockedException(
  message: String,
) : CrdWebException(
  message = message,
  status = HttpStatus.LOCKED,
  code = "NOMIS_LOCKED",
)
