package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.remand.OffenderCustodyPeriod
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.remand.OffenderPeriodsOfCustodyService

@RestController
@Tag(name = "Offender Custody Periods", description = "Offender custody periods")
class OffenderCustodyPeriodsController(private val offenderPeriodsOfCustodyService: OffenderPeriodsOfCustodyService) {

  @GetMapping("/offender-custody-periods/{prisonerId}")
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__CALCULATE__RW', 'CALCULATE_RELEASE_DATES__CALCULATE__RO')")
  @ResponseBody
  @Operation(
    summary = "Get ",
    description = "This endpoint will return a list of custody periods for an offender",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a List<OffenderCustodyPeriod>"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun offenderRemandPeriods(prisonerId: String): List<OffenderCustodyPeriod> = offenderPeriodsOfCustodyService.offenderRemandPeriods(prisonerId)
}
