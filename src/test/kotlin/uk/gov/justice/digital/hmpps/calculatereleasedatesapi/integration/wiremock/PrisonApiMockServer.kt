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
    const val VALID_BOOKING_ID = 9292L
    const val VALID_PRISONER_ID = "A1234AA"

    const val UNSUPPORTED_SENTENCE_BOOKING_ID = 123L
    const val UNSUPPORTED_SENTENCE_VALID_PRISONER_ID = "Z4321AB"
  }

  override fun beforeAll(context: ExtensionContext) {
    prisonApi.start()
    prisonApi.stubGetPrisonerDetails(VALID_PRISONER_ID, VALID_BOOKING_ID)
    prisonApi.stubGetSentencesAndOffences(VALID_BOOKING_ID)
    prisonApi.stubGetSentenceAdjustments(VALID_BOOKING_ID)
    prisonApi.stubPostOffenderDates(VALID_BOOKING_ID)

    prisonApi.stubGetPrisonerDetails(UNSUPPORTED_SENTENCE_VALID_PRISONER_ID, UNSUPPORTED_SENTENCE_BOOKING_ID)
    prisonApi.stubGetSentencesAndOffencesUnsupportedSentences(UNSUPPORTED_SENTENCE_BOOKING_ID)
    prisonApi.stubGetSentenceAdjustments(UNSUPPORTED_SENTENCE_BOOKING_ID)
    prisonApi.stubPostOffenderDates(UNSUPPORTED_SENTENCE_BOOKING_ID)
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

  fun stubGetSentencesAndOffencesUnsupportedSentences(bookingId: Long): StubMapping =
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
                "sentenceCalculationType": "UNSUPPORTED",
                "sentenceTypeDescription": "This sentence is unsupported",
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
              },
              {
                "bookingId": $bookingId,
                "sentenceSequence": 2,
                "sentenceStatus": "A",
                "sentenceCategory": "2003",
                "sentenceCalculationType": "UNSUPPORTED_ORA",
                "sentenceTypeDescription": "This sentence is also unsupported",
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
