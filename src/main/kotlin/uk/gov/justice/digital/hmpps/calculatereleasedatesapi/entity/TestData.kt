package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "test_data")
data class TestData(
  @Id
  val key: String = "",

  val value: String = "",
)
