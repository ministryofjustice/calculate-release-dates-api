package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NonFridayReleaseDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.NonFridayReleaseService
import java.time.LocalDate

@RestController
@RequestMapping("/non-friday-release", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "non-friday-release-controller", description = "Discretionary Friday/pre-Bank Holiday release scheme. Releasing on a day which has a full working day after it.")
@Validated
class NonFridayReleaseController(
  private val nonFridayReleaseService: NonFridayReleaseService,
) {

  @GetMapping(value = ["/{date}"])
  @ResponseBody
  @Operation(
    summary = "Find the non friday release day from a given date",
    description = "Finds the non friday release day, adjusting for weekends and bank holidays",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns non friday release day"),
      ApiResponse(responseCode = "400", description = "Bad Request"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun nonFridayReleaseDay(
    @Parameter(required = true, example = "2021-10-28", description = "The date to adjust")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PathVariable("date")
    @Future
    date: LocalDate,
  ): NonFridayReleaseDay {
    log.info("Request received to non friday release day from $date")
    return nonFridayReleaseService.getDate(date)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
