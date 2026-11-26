package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table
data class LegacyCalculationReason(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long,
  val code: String,
  val description: String,
  @JoinColumn(name = "crds_reason_id")
  @OneToOne(fetch = FetchType.EAGER)
  val calculationReason: CalculationReason? = null,
)
