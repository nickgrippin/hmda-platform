package hmda.persistence.processing

import java.time.Instant

import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import hmda.model.fi._
import hmda.model.util.FITestData._
import hmda.persistence.messages.CommonMessages.GetState
import hmda.persistence.model.ActorSpec
import hmda.persistence.processing.HmdaRawFile.AddLine
import hmda.persistence.processing.ProcessingMessages.{ CompleteUpload, StartUpload }
import hmda.persistence.processing.SubmissionManager._
import hmda.persistence.institutions.FilingPersistence._

class SubmissionManagerSpec extends ActorSpec {
  val config = ConfigFactory.load()

  val submissionId = SubmissionId("0", "testPeriod", 1)

  val submissionManager = createSubmissionManager(system, submissionId)
  val filingPersistence = createFilings("0", system)

  val probe = TestProbe()

  val filing = Filing("testPeriod", "0", NotStarted, filingRequired = false, 0L, 0L)
  probe.send(filingPersistence, CreateFiling(filing))
  probe.expectMsg(Some(filing))
  Thread.sleep(1000)

  val lines = fiCSV.split("\n")
  val timestamp = Instant.now.toEpochMilli

  "A HMDA File" must {
    "upload, parse and validate" in {
      probe.send(filingPersistence, GetFilingByPeriod("testPeriod"))
      val filingNotStarted = probe.expectMsgType[Filing]
      filingNotStarted.status mustBe NotStarted

      probe.send(submissionManager, StartUpload)
      Thread.sleep(1000)
      probe.send(filingPersistence, GetFilingByPeriod("testPeriod"))
      val filingInProgress = probe.expectMsgType[Filing]
      filingInProgress.status mustBe InProgress

      for (line <- lines) {
        probe.send(submissionManager, AddLine(timestamp, line.toString))
      }
      probe.send(submissionManager, CompleteUpload)
      probe.send(submissionManager, GetState)
      probe.expectMsg(Uploaded)
      Thread.sleep(5000) //TODO: can this be avoided?
      probe.send(submissionManager, GetState)
      probe.expectMsg(ValidatedWithErrors)
    }

  }

}
