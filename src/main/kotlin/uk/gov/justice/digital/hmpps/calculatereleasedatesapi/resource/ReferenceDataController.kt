package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DateTypeDefinition
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

@RestController
@RequestMapping("/reference-data", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "reference-data-controller", description = "Provides basic reference data")
@Validated
class ReferenceDataController {

  @GetMapping(value = ["/date-type"])
  @ResponseBody
  @Operation(
    summary = "Get the date type definitions",
    description = "Returns the date types and their definitions",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns the date types and their definitions"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun getDateTypeDefinitions(): List<DateTypeDefinition> {
    return ReleaseDateType.entries.map { DateTypeDefinition(it.name, it.description) }
  }
}
