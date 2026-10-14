package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DeletedPreviousCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DeleteOldPreliminaryCalculationsService

@RestController
@RequestMapping("/scheduled-task", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Scheduled Tasks", description = "Endpoints for scheduled admin tasks that can only be called locally")
class ScheduledTaskController(private val deleteOldPreliminaryCalculationsService: DeleteOldPreliminaryCalculationsService) {

  @PostMapping(value = ["/delete-old-preliminary-calculations"])
  @ResponseBody
  @Operation(
    summary = "Delete preliminary calculations older than the configured amount",
    description = "An API that can only be called locally within an environment to allow it to be triggered by a cron job. " +
      "It will delete preliminary calculations that are over $MIN_AGE_YEARS years old and a maximum of $MAX_ITEMS at a time",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "The task was processed and a count of deleted calculations returned"),
      ApiResponse(responseCode = "401", description = "Unauthorised, can only be called by trusted services within the namespace and not through ingress"),
      ApiResponse(responseCode = "404", description = "Couldn't find the requested prisoner"),
    ],
  )
  fun deleteOldPreliminaryCalculations(): ResponseEntity<DeletedPreviousCalculationResult> {
    log.info("Received request to delete preliminary calculations older than $MIN_AGE_YEARS years old with a maximum of $MAX_ITEMS removed in one go")
    val result = deleteOldPreliminaryCalculationsService.deleteBatch(MIN_AGE_YEARS, MAX_ITEMS)
    log.info("Successfully deleted ${result.numberDeletedSuccessfully} of ${result.numberFound} old preliminary calculations.")
    return ResponseEntity.ok(result)
  }

  companion object {
    private val log = LoggerFactory.getLogger(ScheduledTaskController::class.java)
    const val MAX_ITEMS = 50
    const val MIN_AGE_YEARS = 2
  }
}
