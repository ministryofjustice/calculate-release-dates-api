package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ComparisonService

@RestController
@RequestMapping("/comparison", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "comparison-controller", description = "Operations performing a comparison")
class ComparisonController(
  private val comparisonService: ComparisonService,
) {

  @PostMapping
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'ROLE_RELEASE_DATE_COMPARER', 'ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "Create a record of a new calculation ",
    description = "This endpoint will create a new calculation and return the new calculation object",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a new Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun create(
    @RequestBody
    comparison: ComparisonInput,
  ): uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison {
    ComparisonController.log.info("Request received to create a new Comparison")
    return comparisonService.create(comparison)
  }

  @GetMapping(value = ["/manual"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "List all comparisons which were performed manually",
    description = "This endpoint will return all of the manually performed calculations recorded in the service. This is not limited by caseload, but requires the MANUAL_COMPARER role.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a list of comparisons Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getManualComparisons(): List<uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison> {
    ComparisonController.log.info("Requested a list of manual Comparisons")
    return comparisonService.listManual()
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'ROLE_RELEASE_DATE_COMPARER', 'ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "List all Comparisons performed using presets",
    description = "This endpoint will return all the comparisons for your caseload",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a list of comparisons Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getComparisons(): List<uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison> {
    ComparisonController.log.info("Requested a list of Comparisons")
    return comparisonService.listComparisons()
  }

  @GetMapping(value = ["{comparisonReference}/count/"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'ROLE_RELEASE_DATE_COMPARER', 'ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "Returns a count of the number of people compared for a particular caseload",
    description = "This endpoint will count all the people associated to a specific comparison",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a list of comparisons Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getCountOfPersonsInComparison(
    @Parameter(required = true, example = "A1B2C3D4", description = "The short reference of the comparison")
    @PathVariable("comparisonReference")
    comparisonReference: String,
  ): Long {
    ComparisonController.log.info("Requested a count of the number of persons in a particular comparison")
    return comparisonService.getCountOfPersonsInComparisonByComparisonReference(comparisonReference)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
