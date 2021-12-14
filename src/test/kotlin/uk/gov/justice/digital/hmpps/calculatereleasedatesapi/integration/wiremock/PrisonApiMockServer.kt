package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class PrisonApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val prisonApi = PrisonApiMockServer()
    const val BOOKING_ID = 9292L
    const val PRISONER_ID = "A1234AA"
  }

  override fun beforeAll(context: ExtensionContext) {
    prisonApi.start()
    prisonApi.stubGetPrisonerDetails(PRISONER_ID)
    prisonApi.stubGetSentencesAndOffences(BOOKING_ID)
    prisonApi.stubGetSentenceAdjustments(BOOKING_ID)
    prisonApi.stubPostOffenderDates(BOOKING_ID)
  }

  override fun beforeEach(context: ExtensionContext) {
    prisonApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    prisonApi.stop()
  }
}

class PrisonApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8332
  }

  fun stubGetPrisonerDetails(prisonerId: String): StubMapping =
    stubFor(
      get("/api/offenders/$prisonerId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            {
              "bookingId": "${PrisonApiExtension.BOOKING_ID}",
              "offenderNo": "$prisonerId",
              "dateOfBirth": "1990-02-01"
            }
              """.trimIndent()
            )
            .withStatus(200)
        )
    )

  fun stubGetSentencesAndOffences(bookingId: Long): StubMapping =
    stubFor(
      get("/api/offender-sentences/booking/$bookingId/sentences-and-offences")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
             [{"bookingId":1201724,"sentenceSequence":1,"lineSequence":1,"caseSequence":1,"sentenceStatus":"A","sentenceCategory":"2020","sentenceCalculationType":"ADIMP_ORA","sentenceTypeDescription":"ORA Sentencing Code Standard Determinate Sentence","sentenceDate":"2021-05-01","years":0,"months":3,"weeks":0,"days":0,"offences":[{"offenderChargeId":3932600,"offenceStartDate":"2020-06-02","offenceCode":"DD91011","offenceDescription":"Abandoning fighting dog"}]},{"bookingId":1201724,"sentenceSequence":2,"consecutiveToSequence":1,"lineSequence":2,"caseSequence":1,"sentenceStatus":"A","sentenceCategory":"2003","sentenceCalculationType":"ADIMP_ORA","sentenceTypeDescription":"ORA CJA03 Standard Determinate Sentence","sentenceDate":"2021-05-01","years":0,"months":2,"weeks":2,"days":2,"offences":[{"offenderChargeId":3932601,"offenceStartDate":"2020-08-02","offenceCode":"WE13095","offenceDescription":"AATF operator/approved exporter fail to include reg 66(8) details in providing report"}]},{"bookingId":1201724,"sentenceSequence":9,"lineSequence":3,"caseSequence":2,"sentenceStatus":"A","sentenceCategory":"2020","sentenceCalculationType":"ADIMP_ORA","sentenceTypeDescription":"ORA Sentencing Code Standard Determinate Sentence","sentenceDate":"2021-06-01","years":0,"months":1,"weeks":1,"days":1,"offences":[{"offenderChargeId":3932602,"offenceStartDate":"2020-12-02","offenceCode":"HP20006","offenceDescription":"Abscond / attempt to abscond from detention under regulation 4 or 5 or 8 - Coronavirus"}]},{"bookingId":1201724,"sentenceSequence":16,"lineSequence":4,"caseSequence":2,"sentenceStatus":"A","sentenceCategory":"2020","sentenceCalculationType":"YOI_ORA","sentenceTypeDescription":"ORA Young Offender Institution","sentenceDate":"2021-06-01","years":0,"months":1,"weeks":0,"days":0,"offences":[{"offenderChargeId":3932603,"offenceStartDate":"2019-01-01","offenceCode":"IA06005","offenceDescription":"Abscond from detention under s.40(7)(c)"}]},{"bookingId":1201724,"sentenceSequence":23,"lineSequence":5,"caseSequence":3,"sentenceStatus":"A","sentenceCategory":"2020","sentenceCalculationType":"ADIMP_ORA","sentenceTypeDescription":"ORA Sentencing Code Standard Determinate Sentence","sentenceDate":"2021-08-01","years":0,"months":5,"weeks":0,"days":2,"offences":[{"offenderChargeId":3932604,"offenceStartDate":"2021-01-01","offenceCode":"BA12035","offenceDescription":"Abandon vehicle on Bristol Airport"}]}]
              """.trimIndent()
            )
            .withStatus(200)
        )
    )

  fun stubGetSentenceAdjustments(bookingId: Long): StubMapping =
    stubFor(
      get("/api/bookings/$bookingId/sentenceAdjustments")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "additionalDaysAwarded": 0,
                "lawfullyAtLarge": 0,
                "recallSentenceRemand": 0,
                "recallSentenceTaggedBail": 0,
                "remand": 28,
                "restoredAdditionalDaysAwarded": 0,
                "specialRemission": 0,
                "taggedBail": 11,
                "unlawfullyAtLarge": 29,
                "unusedRemand": 0
              }
              """.trimIndent()
            )
            .withStatus(200)
        )
    )

  fun stubPostOffenderDates(bookingId: Long): StubMapping =
    stubFor(
      post("/api/offender-dates/$bookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
        )
    )
}
