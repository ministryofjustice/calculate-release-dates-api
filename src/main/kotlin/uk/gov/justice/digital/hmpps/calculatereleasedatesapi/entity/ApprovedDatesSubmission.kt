package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.hibernate.Hibernate
import java.time.LocalDateTime

@Entity
@Table
data class ApprovedDatesSubmission(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,
  @NotNull
  @ManyToOne(optional = false)
  @JoinColumn(name = "calculationRequestId", nullable = false, updatable = false)
  val calculationRequest: CalculationRequest,
  @NotNull
  val prisonerId: String,
  @NotNull
  val bookingId: Long,
  @NotNull
  val submittedAt: LocalDateTime = LocalDateTime.now(),
  @NotNull
  val submittedByUsername: String,
  @NotNull
  @OneToMany(targetEntity = ApprovedDates::class, cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
  val approvedDates: List<ApprovedDates> = emptyList(),
) {
  @Override
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as ApprovedDatesSubmission

    return id == other.id
  }

  @Override
  override fun hashCode(): Int = javaClass.hashCode()
}
