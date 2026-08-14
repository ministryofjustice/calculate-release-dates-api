package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import java.time.LocalDate

data class CalculationSnapshot(val name: SnapshotName, val result: CalculationResult, val dateAtWhichSnapshotTaken: LocalDate)
