package uk.gov.justice.digital.hmpps.calculatereleasedatesapi

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.CalculationTestFile
import java.text.SimpleDateFormat
import java.time.LocalDate
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

class TestUtil private constructor() {
  companion object {

    fun objectMapper(): ObjectMapper = jacksonObjectMapper().apply {
      registerModule(JavaTimeModule())
      dateFormat = SimpleDateFormat("yyyy-MM-dd")
      configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun minimalTestCaseMapper(): ObjectMapper = objectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT)

    fun defaultBankHolidays(): BankHolidays = BankHolidays(
      RegionBankHolidays(
        "England and Wales",
        listOf(
          BankHoliday("Christmas Day Bank Holiday", LocalDate.of(2021, 12, 27)),
          BankHoliday("Boxing Day Bank Holiday", LocalDate.of(2021, 12, 28)),
        ),
      ),
      RegionBankHolidays("Scotland", emptyList()),
      RegionBankHolidays("Northern Ireland", emptyList()),
    )

    fun overrideFeatureTogglesForTest(calculationTestFile: CalculationTestFile, featureToggles: FeatureToggles) {
      if (calculationTestFile.featureToggles != null) {
        overwriteProperties(featureToggles, calculationTestFile.featureToggles)
      } else {
        overwriteProperties(featureToggles, FeatureToggles(ftr48ManualJourney = true, useERS30Calculation = true))
      }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> overwriteProperties(target: T, source: T) {
      val kClass = T::class
      for (property in kClass.memberProperties) {
        if (property is KMutableProperty1) {
          val mutableProp = property as KMutableProperty1<T, Any?>
          val value = mutableProp.get(source)
          mutableProp.set(target, value)
        }
      }
    }
  }
}
