package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideCreatedResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideInputResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReasonResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.CalculationController.Companion.log
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.GenuineOverrideService

@RestController
@RequestMapping("/genuine-override", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "genuine-override-controller", description = "Genuine override related endpoints")
class GenuineOverrideController(private val genuineOverrideService: GenuineOverrideService) {

  @GetMapping("/reasons")
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get the reasons for overriding dates",
    description = "Get the reasons for overriding dates along with descriptions and whether further detail is required",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns list of reasons"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getGenuineOverrideReasons(): List<GenuineOverrideReasonResponse> = GenuineOverrideReason.entries.map { it.toResponse() }

  @PostMapping(value = ["/calculation/{calculationRequestId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Override the dates for a given calculation",
    description = "Replace the calculated dates with a dates that may have been added, removed or modified by OMU",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Dates were successfully overridden"),
      ApiResponse(responseCode = "400", description = "If the supplied dates are invalid"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(
        responseCode = "404",
        description = "Couldn't find the requested calculation or it's in an invalid state",
      ),
    ],
  )
  fun storeGenuineOverrideForCalculation(
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
    @RequestBody
    request: GenuineOverrideRequest,
  ): ResponseEntity<GenuineOverrideCreatedResponse> {
    log.info("Request received to override release dates for calculation $calculationRequestId")
    val response = genuineOverrideService.overrideDatesForACalculation(calculationRequestId, request)
    return if (response.success) {
      ResponseEntity.status(HttpStatus.OK).body(response)
    } else {
      ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }
  }

  @GetMapping(value = ["/calculation/{calculationRequestId}/inputs"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get the inputs for a genuine override based on a preliminary calculation",
    description = "Get the mode (standard or express) for a genuine override on this calculation as well as the calculated dates " +
      "and a previous overrides details if this override can be express",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "The inputs for doing a genuine override on a calculation"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(
        responseCode = "404",
        description = "Couldn't find the requested calculation or it's in an invalid state",
      ),
    ],
  )
  fun getInputsForGenuineOverrideForCalculation(
    @PathVariable("calculationRequestId")
    calculationRequestId: Long,
  ): GenuineOverrideInputResponse = genuineOverrideService.inputsForCalculation(calculationRequestId)
}
