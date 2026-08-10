package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureTogglesTest {

  @Test
  fun `Describe all the feature toggles`() {
    assertThat(FeatureToggles().toConfigItems()).hasSize(FeatureToggles::class.java.declaredFields.size)
  }
}
