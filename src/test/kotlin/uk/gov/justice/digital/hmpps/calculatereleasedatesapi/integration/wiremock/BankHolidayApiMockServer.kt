package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class BankHolidayApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val bankholidayApi = BankHolidayApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    bankholidayApi.start()
    bankholidayApi.stubBankHolidays()
  }

  override fun beforeEach(context: ExtensionContext) {
    bankholidayApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    bankholidayApi.stop()
  }
}

@Suppress("LargeClass")
class BankHolidayApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8333
  }

  @Suppress("LongMethod")
  fun stubBankHolidays(): StubMapping = stubFor(
    get("/bank-holidays.json")
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """{
                 "england-and-wales":{
                    "division":"england-and-wales",
                    "events":[
                       {
                          "title":"New Year’s Day",
                          "date":"2016-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2016-03-25",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2016-03-28",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2016-05-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2016-05-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2016-08-29",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2016-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2016-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2017-01-02",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2017-04-14",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2017-04-17",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2017-05-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2017-05-29",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2017-08-28",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2017-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2017-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2018-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2018-03-30",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2018-04-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2018-05-07",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2018-05-28",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2018-08-27",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2018-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2018-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2019-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2019-04-19",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2019-04-22",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2019-05-06",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2019-05-27",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2019-08-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2019-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2019-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2020-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2020-04-10",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2020-04-13",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday (VE day)",
                          "date":"2020-05-08",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2020-05-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2020-08-31",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2020-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2020-12-28",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2021-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2021-04-02",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2021-04-05",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2021-05-03",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2021-05-31",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2021-08-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2021-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2021-12-28",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2022-01-03",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2022-04-15",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2022-04-18",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2022-05-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2022-06-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Platinum Jubilee bank holiday",
                          "date":"2022-06-03",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2022-08-29",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2022-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2022-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       }
                    ]
                 },
                 "scotland":{
                    "division":"scotland",
                    "events":[
                       {
                          "title":"New Year’s Day",
                          "date":"2016-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"2nd January",
                          "date":"2016-01-04",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2016-03-25",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2016-05-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2016-05-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2016-08-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Andrew’s Day",
                          "date":"2016-11-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2016-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2016-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"2nd January",
                          "date":"2017-01-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2017-01-03",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2017-04-14",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2017-05-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2017-05-29",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2017-08-07",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Andrew’s Day",
                          "date":"2017-11-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2017-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2017-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2018-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"2nd January",
                          "date":"2018-01-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2018-03-30",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2018-05-07",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2018-05-28",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2018-08-06",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Andrew’s Day",
                          "date":"2018-11-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2018-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2018-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2019-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"2nd January",
                          "date":"2019-01-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2019-04-19",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2019-05-06",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2019-05-27",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2019-08-05",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Andrew’s Day",
                          "date":"2019-12-02",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2019-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2019-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2020-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"2nd January",
                          "date":"2020-01-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2020-04-10",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday (VE day)",
                          "date":"2020-05-08",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2020-05-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2020-08-03",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Andrew’s Day",
                          "date":"2020-11-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2020-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2020-12-28",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2021-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"2nd January",
                          "date":"2021-01-04",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2021-04-02",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2021-05-03",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2021-05-31",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2021-08-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Andrew’s Day",
                          "date":"2021-11-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2021-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2021-12-28",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2022-01-03",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"2nd January",
                          "date":"2022-01-04",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2022-04-15",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2022-05-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2022-06-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Platinum Jubilee bank holiday",
                          "date":"2022-06-03",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2022-08-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Andrew’s Day",
                          "date":"2022-11-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2022-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2022-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       }
                    ]
                 },
                 "northern-ireland":{
                    "division":"northern-ireland",
                    "events":[
                       {
                          "title":"New Year’s Day",
                          "date":"2016-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Patrick’s Day",
                          "date":"2016-03-17",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2016-03-25",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2016-03-28",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2016-05-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2016-05-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Battle of the Boyne (Orangemen’s Day)",
                          "date":"2016-07-12",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2016-08-29",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2016-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2016-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2017-01-02",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"St Patrick’s Day",
                          "date":"2017-03-17",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2017-04-14",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2017-04-17",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2017-05-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2017-05-29",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Battle of the Boyne (Orangemen’s Day)",
                          "date":"2017-07-12",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2017-08-28",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2017-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2017-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2018-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Patrick’s Day",
                          "date":"2018-03-19",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2018-03-30",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2018-04-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2018-05-07",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2018-05-28",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Battle of the Boyne (Orangemen’s Day)",
                          "date":"2018-07-12",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2018-08-27",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2018-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2018-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2019-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Patrick’s Day",
                          "date":"2019-03-18",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2019-04-19",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2019-04-22",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2019-05-06",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2019-05-27",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Battle of the Boyne (Orangemen’s Day)",
                          "date":"2019-07-12",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2019-08-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2019-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2019-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2020-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Patrick’s Day",
                          "date":"2020-03-17",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2020-04-10",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2020-04-13",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Early May bank holiday (VE day)",
                          "date":"2020-05-08",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2020-05-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Battle of the Boyne (Orangemen’s Day)",
                          "date":"2020-07-13",
                          "notes":"Substitute day",
                          "bunting":false
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2020-08-31",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2020-12-25",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2020-12-28",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2021-01-01",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"St Patrick’s Day",
                          "date":"2021-03-17",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2021-04-02",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2021-04-05",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2021-05-03",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2021-05-31",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Battle of the Boyne (Orangemen’s Day)",
                          "date":"2021-07-12",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2021-08-30",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2021-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2021-12-28",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"New Year’s Day",
                          "date":"2022-01-03",
                          "notes":"Substitute day",
                          "bunting":true
                       },
                       {
                          "title":"St Patrick’s Day",
                          "date":"2022-03-17",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Good Friday",
                          "date":"2022-04-15",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Easter Monday",
                          "date":"2022-04-18",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Early May bank holiday",
                          "date":"2022-05-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Spring bank holiday",
                          "date":"2022-06-02",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Platinum Jubilee bank holiday",
                          "date":"2022-06-03",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Battle of the Boyne (Orangemen’s Day)",
                          "date":"2022-07-12",
                          "notes":"",
                          "bunting":false
                       },
                       {
                          "title":"Summer bank holiday",
                          "date":"2022-08-29",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Boxing Day",
                          "date":"2022-12-26",
                          "notes":"",
                          "bunting":true
                       },
                       {
                          "title":"Christmas Day",
                          "date":"2022-12-27",
                          "notes":"Substitute day",
                          "bunting":true
                       }
                    ]
                 }
              }
            """.trimIndent(),
          )
          .withStatus(200),
      ),
  )
}
