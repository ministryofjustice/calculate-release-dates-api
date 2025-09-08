package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReasonResponse

@RestController
@RequestMapping("/genuine-override-reasons", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "genuine-override-reasons-controller", description = "Returns a list of reasons for entering a genuine override")
class GenuineOverrideReasonController {

  @GetMapping("/")
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns list of reasons"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getGenuineOverrideReasons(): List<GenuineOverrideReasonResponse> = GenuineOverrideReason.entries.map { it.toResponse() }
}
