package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.TestData as EntityTestData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.TestData as ModelTestData

/*
** Functions which transform entities objects into their model equivalents.
** Sometimes a pass-thru but very useful when objects need to be altered or enriched
*/

fun transform(testData: EntityTestData): ModelTestData {
  return ModelTestData(
    key = testData.key,
    value = testData.value
  )
}
