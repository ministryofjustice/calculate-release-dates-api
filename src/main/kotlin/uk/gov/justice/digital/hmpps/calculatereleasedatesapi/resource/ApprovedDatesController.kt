package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesInputResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ApprovedDatesService

@RestController
@RequestMapping("/approved-dates", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "approved-dates-controller", description = "Endpoints related to adding approved dates to an existing calculation")
class ApprovedDatesController(private val approvedDatesService: ApprovedDatesService) {

  @GetMapping(value = ["/{prisonerId}/inputs"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get the inputs required for adding approved dates for a prisoner",
    description = "Checks whether approved dates can be added or whether a full calculation is required. " +
      "If approved dates can be added, a preliminary calculation is created." +
      "If there are previously entered approved dates that are still relevant those are also returned",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "The inputs required for adding approved dates for a prisoner"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "Couldn't find the requested prisoner"),
    ],
  )
  fun getInputsForGenuineOverrideForCalculation(
    @PathVariable("prisonerId")
    prisonerId: String,
  ): ApprovedDatesInputResponse = approvedDatesService.inputsForPrisoner(prisonerId)
}
