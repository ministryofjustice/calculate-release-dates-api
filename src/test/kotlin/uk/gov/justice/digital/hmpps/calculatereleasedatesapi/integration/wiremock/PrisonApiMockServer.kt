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
    const val PRISONER_ID_ERROR = "123CBA"
    const val BOOKING_ID_ERROR = 123L
  }

  override fun beforeAll(context: ExtensionContext) {
    prisonApi.start()
    prisonApi.stubGetPrisonerDetails(PRISONER_ID, BOOKING_ID)
    prisonApi.stubGetSentencesAndOffences(BOOKING_ID)
    prisonApi.stubGetSentenceAdjustments(BOOKING_ID)
    prisonApi.stubPostOffenderDates(BOOKING_ID)

    prisonApi.stubGetPrisonerDetails(PRISONER_ID_ERROR, BOOKING_ID_ERROR)
    prisonApi.stubGetErrorSentencesAndOffences(BOOKING_ID_ERROR)
    prisonApi.stubGetSentenceAdjustments(BOOKING_ID_ERROR)
    prisonApi.stubPostOffenderDates(BOOKING_ID_ERROR)
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

  fun stubGetPrisonerDetails(prisonerId: String, bookingId: Long): StubMapping =
    stubFor(
      get("/api/offenders/$prisonerId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            {
              "bookingId": "$bookingId",
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
            [
              {
                "bookingId": $bookingId,
                "sentenceSequence": 1,
                "sentenceStatus": "A",
                "sentenceCategory": "2003",
                "sentenceCalculationType": "SDS",
                "sentenceTypeDescription": "Standard Determinate",
                "sentenceDate": "2015-03-17",
                "years": 0,
                "months": 20,
                "weeks": 0,
                "days": 0,
                "offences": [
                  {
                    "offenderChargeId": 9991,
                    "offenceStartDate": "2015-03-17",
                    "offenceCode": "GBH",
                    "offenceDescription": "Grievous bodily harm"
                  }
                ]
              }
            ]
              """.trimIndent()
            )
            .withStatus(200)
        )
    )

  fun stubGetErrorSentencesAndOffences(bookingId: Long): StubMapping =
    stubFor(
      get("/api/offender-sentences/booking/$bookingId/sentences-and-offences")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            [
   {
      "bookingId":$bookingId,
      "sentenceSequence":1,
      "lineSequence":1,
      "caseSequence":2,
      "sentenceStatus":"I",
      "sentenceCategory":"2003",
      "sentenceCalculationType":"ADIMP_ORA",
      "sentenceTypeDescription":"ORA CJA03 Standard Determinate Sentence",
      "sentenceDate":"2018-10-09",
      "years":0,
      "months":0,
      "weeks":4,
      "days":0,
      "offences":[
         {
            "offenderChargeId":6115401,
            "offenceStartDate":"2020-04-02",
            "offenceCode":"TH68007",
            "offenceDescription":"Theft from a motor vehicle"
         }
      ]
   },
   {
      "bookingId":$bookingId,
      "sentenceSequence":2,
      "lineSequence":2,
      "caseSequence":1,
      "sentenceStatus":"A",
      "sentenceCategory":"2003",
      "sentenceCalculationType":"ADIMP",
      "sentenceTypeDescription":"CJA03 Standard Determinate Sentence",
      "sentenceDate":"2020-11-11",
      "years":0,
      "months":90,
      "weeks":0,
      "days":0,
      "offences":[
         {
            "offenderChargeId":5987824,
            "offenceStartDate":"2020-05-16",
            "offenceCode":"TH68023",
            "offenceDescription":"Robbery"
         }
      ]
   },
   {
      "bookingId":$bookingId,
      "sentenceSequence":3,
      "lineSequence":3,
      "caseSequence":1,
      "sentenceStatus":"A",
      "sentenceCategory":"2003",
      "sentenceCalculationType":"ADIMP",
      "sentenceTypeDescription":"CJA03 Standard Determinate Sentence",
      "sentenceDate":"2020-11-11",
      "years":2,
      "months":0,
      "weeks":0,
      "days":0,
      "offences":[
         {
            "offenderChargeId":5987825,
            "offenceStartDate":"2020-05-16",
            "offenceCode":"FI68321",
            "offenceDescription":"Possess an imitation firearm with intent to cause fear of violence"
         }
      ]
   },
   {
      "bookingId":$bookingId,
      "sentenceSequence":4,
      "lineSequence":4,
      "caseSequence":1,
      "sentenceStatus":"A",
      "sentenceCategory":"2003",
      "sentenceCalculationType":"ADIMP_ORA",
      "sentenceTypeDescription":"ORA CJA03 Standard Determinate Sentence",
      "sentenceDate":"2020-11-11",
      "years":0,
      "months":6,
      "weeks":0,
      "days":0,
      "offences":[
         {
            "offenderChargeId":5987826,
            "offenceStartDate":"2020-05-17",
            "offenceCode":"MD71211",
            "offenceDescription":"Possessing controlled drug - Class A - diamorphine (heroin)"
         }
      ]
   }
]
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
