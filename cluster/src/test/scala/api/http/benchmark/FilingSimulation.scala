package api.http.benchmark

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.language.postfixOps

class FilingSimulation extends Simulation {

  val config = ConfigFactory.load()
  val host = config.getString("hmda.benchmark.host")
  val port = config.getInt("hmda.benchmark.port")
  val adminPort = config.getInt("hmda.benchmark.adminPort")
  val nrOfUsers = config.getInt("hmda.benchmark.nrOfUsers")
  val rampUpTime = config.getInt("hmda.benchmark.rampUpTime")
  val feeder = csv(config.getString("hmda.benchmark.feederFile")).random

  val httpProtocol = http
    .baseURL(s"http://$host:$port")
    .acceptHeader("text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
    .header("cfpb-hmda-username", "user")
    .disableCaching

  object Institutions {

    val hmdaFilingApi =
      feed(feeder)
        .exec(http("Search Institutions")
          .get("/institutions")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .check(status is 200))
        .pause(1)
        .exec(http("Institution by id")
          .get("/institutions/${institutionId}")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .check(status is 200)
          .check(jsonPath("$.institution.id") is "${institutionId}"))
        .pause(1)
        .exec(http("List Filings")
          .get("/institutions/${institutionId}/filings/2017")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .check(status is 200))
        .pause(1)
        .exec(http("Create Submission")
          .post("/institutions/${institutionId}/filings/2017/submissions")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .check(status is 201)
          .check(jsonPath("$.id.sequenceNumber").saveAs("submissionId")))
        .pause(2)
        .exec(http("Upload file")
          .post("/institutions/${institutionId}/filings/2017/submissions/${submissionId}")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .bodyPart(RawFileBodyPart("file", "${fileName}"))
          .check(status is 202))
        .pause(10)
        .exec(http("View edits")
          .get("/institutions/${institutionId}/filings/2017/submissions/${submissionId}/edits")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .check(status is 200)
          .check(jsonPath("$.status.code") is "8"))
        .pause(5)
        .exec(http("Validate Quality Edits")
          .post("/institutions/${institutionId}/filings/2017/submissions/${submissionId}/edits/quality")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .body(StringBody("""{ "verified": true }""")).asJSON
          .check(status is 200))
        .pause(1)
        .exec(http("Validate Macro Edits")
          .post("/institutions/${institutionId}/filings/2017/submissions/${submissionId}/edits/macro")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .body(StringBody("""{ "verified": true }""")).asJSON
          .check(status is 200))
        .pause(1)
        .exec(http("Get Summary")
          .get("/institutions/${institutionId}/filings/2017/submissions/${submissionId}/summary")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .check(status is 200))
        .pause(1)
        .exec(http("IRS Report")
          .get("/institutions/${institutionId}/filings/2017/submissions/${submissionId}/irs")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .check(status is 200))
        .pause(1)
        .exec(http("Sign Submission")
          .post("/institutions/${institutionId}/filings/2017/submissions/${submissionId}/sign")
          .header("cfpb-hmda-institutions", "${institutionId}")
          .body(StringBody(""" { "signed": true } """)).asJSON
          .check(status is 200)
          .check(jsonPath("$.status.code") is "10"))

  }

  val user = scenario("HMDA User")
    .exec(Institutions.hmdaFilingApi)

  setUp(
    user.inject(rampUsers(nrOfUsers) over (rampUpTime seconds)).protocols(httpProtocol)
  )

}
