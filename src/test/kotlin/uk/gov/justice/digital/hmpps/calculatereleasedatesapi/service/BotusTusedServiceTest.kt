package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource.CRDS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource.CRDS_OVERRIDDEN
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource.NOMIS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource.NOMIS_OVERRIDDEN
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricalTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class BotusTusedServiceTest {

  @Mock
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @InjectMocks
  lateinit var botusTusedService: BotusTusedService

  @Test
  fun `Check that historical TUSEDs from NOMIS are correctly categorised `() {
    whenever(calculationRequestRepository.findByCalculationReference(any())).thenReturn(Optional.of(CalculationRequest()))

    assertThat(botusTusedService.identifyTused(CALCULATED_DATE_WITH_CRD_COMMENT)).isEqualTo(
      HistoricalTusedData(
        FIRST_OF_MARCH,
        CRDS,
      ),
    )

    assertThat(botusTusedService.identifyTused(OVERRIDDEN_DATE_WITH_CRD_COMMENT)).isEqualTo(
      HistoricalTusedData(
        FIRST_OF_APRIL,
        CRDS_OVERRIDDEN,
      ),
    )

    assertThat(botusTusedService.identifyTused(CALCULATED_DATE_WITH_NULL_COMMENT)).isEqualTo(
      HistoricalTusedData(
        FIRST_OF_MARCH,
        NOMIS,
      ),
    )

    assertThat(botusTusedService.identifyTused(CALCULATED_DATE_WITHOUT_CRD_COMMENT)).isEqualTo(
      HistoricalTusedData(
        FIRST_OF_MARCH,
        NOMIS,
      ),
    )

    assertThat(botusTusedService.identifyTused(OVERRRIDDEN_DATE_WITHOUT_CRD_COMMENT)).isEqualTo(
      HistoricalTusedData(
        FIRST_OF_APRIL,
        NOMIS_OVERRIDDEN,
      ),
    )
  }

  private companion object {
    private val CRD_COMMENT = """
    The information shown was calculated using the Calculate Release Dates service. 
    The calculation ID is: d00edc6d-01bf-441b-bb15-d4bc6b7b1cf5
  """

    private val FIRST_OF_MARCH = LocalDate.of(2024, 3, 1)
    private val FIRST_OF_APRIL = LocalDate.of(2024, 4, 1)

    private val CALCULATED_DATE_WITH_CRD_COMMENT = NomisTusedData(FIRST_OF_MARCH, null, CRD_COMMENT, "A1234AB")

    private val CALCULATED_DATE_WITH_NULL_COMMENT = NomisTusedData(FIRST_OF_MARCH, null, null, "A1234AB")

    private val OVERRIDDEN_DATE_WITH_CRD_COMMENT =
      NomisTusedData(FIRST_OF_MARCH, FIRST_OF_APRIL, CRD_COMMENT, "A1234AB")

    private val CALCULATED_DATE_WITHOUT_CRD_COMMENT = NomisTusedData(FIRST_OF_MARCH, null, "UPDATE", "A1234AB")

    private val OVERRRIDDEN_DATE_WITHOUT_CRD_COMMENT =
      NomisTusedData(FIRST_OF_MARCH, FIRST_OF_APRIL, "UPDATE", "A1234AB")
  }
}
