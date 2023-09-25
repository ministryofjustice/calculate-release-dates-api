package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDateRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDateResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.GenuineOverrideService

@RestController
@RequestMapping("/specialist-support", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "specialist-support-controller", description = "Operations for specialist support")
class SpecialistSupportController(
  val genuineOverrideService: GenuineOverrideService,
) {

  @PostMapping(value = ["/genuine-override"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'CRDS_SPECIALIST_SUPPORT')")
  @ResponseBody
  @Operation(
    summary = "Store a genuine override",
    description = "This endpoint will return a response model which indicates the success of storing a genuine override",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a GenuineOverrideResponse"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun storeGenuineOverride(
    @RequestBody genuineOverrideRequest: GenuineOverrideRequest,
  ): GenuineOverrideResponse {
    return genuineOverrideService.createGenuineOverride(genuineOverrideRequest)
  }

  @PostMapping(value = ["/genuine-override/calculation"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'CRDS_SPECIALIST_SUPPORT')")
  @ResponseBody
  @Operation(
    summary = "Store a genuine override",
    description = "This endpoint will return a response model which indicates the success of storing a genuine override",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a GenuineOverrideResponse"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun storeGenuineOverrideDates(@RequestBody genuineOverrideRequest: GenuineOverrideDateRequest): GenuineOverrideDateResponse {
    return genuineOverrideService.storeGenuineOverrideDates(genuineOverrideRequest)
  }

  @GetMapping(value = ["genuine-override/calculation/{calculationReference}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'CRDS_SPECIALIST_SUPPORT')")
  @ResponseBody
  @Operation(
    summary = "Get a genuine override",
    description = "This endpoint will return a response model which returns a genuine override",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a GenuineOverrideResponse"),
      ApiResponse(responseCode = "404", description = "Not Found, a genuine override doesn't exist for the calculation reference"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getGenuineOverride(@PathVariable calculationReference: String): GenuineOverrideResponse {
    return genuineOverrideService.getGenuineOverride(calculationReference)
  }
}
