package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.TestData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TestService

@RestController
@RequestMapping("/test", produces = [MediaType.APPLICATION_JSON_VALUE])
class TestController(private val testService: TestService) {

  @GetMapping(value = ["/data"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'CRD_ADMIN', 'PRISON')")
  @ResponseBody
  @Operation(
    summary = "Get a list of test data",
    description = "Just a test API to verify that the full stack of components are working together",
    security = [
      SecurityRequirement(name = "SYSTEM_USER"),
      SecurityRequirement(name = "CRD_ADMIN"),
      SecurityRequirement(name = "PRISON")
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role")
    ]
  )
  fun getTestData(): List<TestData> {
    return testService.getTestData()
  }
}
