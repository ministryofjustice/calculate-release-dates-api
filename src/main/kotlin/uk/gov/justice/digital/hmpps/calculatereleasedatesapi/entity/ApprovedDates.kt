package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Entity
@Table
data class ApprovedDates(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,
  @NotNull
  @ManyToOne(optional = false)
  @JoinColumn(name = "approvedDatesSubmissionRequestId", nullable = false, updatable = false)
  val approvedDatesSubmissionRequestId: ApprovedDatesSubmission,
  @NotNull
  val calculationDateType: String,
  @NotNull
  val outcomeDate: LocalDate,
)
