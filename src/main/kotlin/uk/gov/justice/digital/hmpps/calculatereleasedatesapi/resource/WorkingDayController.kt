package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.WorkingDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.WorkingDayService
import java.time.LocalDate

@RestController
@RequestMapping("/working-day", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "working-day-controller", description = "Operations working days/bank holidays")
class WorkingDayController(
  private val workingDayService: WorkingDayService,
) {

  @GetMapping(value = ["/next/{date}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Find the next working day from a given date",
    description = "Finds the next working day, adjusting for weekends and bank holidays",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns next working day"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun nextWorkingDay(
    @Parameter(required = true, example = "2021-10-28", description = "The date to adjust")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PathVariable("date")
    date: LocalDate,
  ): WorkingDay {
    log.info("Request received to find next working day from $date")
    return workingDayService.nextWorkingDay(date)
  }

  @GetMapping(value = ["/previous/{date}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Find the previous working day from a given date",
    description = "Finds the previous working day, adjusting for weekends and bank holidays",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns previous working day"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun previousWorkingDay(
    @Parameter(required = true, example = "2021-10-28", description = "The date to adjust")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PathVariable("date")
    date: LocalDate,
  ): WorkingDay {
    log.info("Request received to find previous working day from $date")
    return workingDayService.previousWorkingDay(date)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
