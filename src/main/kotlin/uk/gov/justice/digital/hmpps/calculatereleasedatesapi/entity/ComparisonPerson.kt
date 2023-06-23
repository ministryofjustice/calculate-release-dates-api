package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull

@Entity
@Table
class ComparisonPerson(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = -1,

  @NotNull
  val comparisonId: Int,

  @NotNull
  val person: String,
)

/*
CREATE TABLE comparison_person
(
    id                          serial                        PRIMARY KEY,
    comparison_id               int references comparison(id) NOT NULL,
    person                      varchar(20)                   NOT NULL
);
 */
