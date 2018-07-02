package hmda.publication.reports.disclosure

import akka.{ Done, NotUsed }
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{ Subscribe, SubscribeAck }
import akka.pattern.ask
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }
import akka.stream.Supervision._
import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.alpakka.s3.{ MemoryBufferType, S3Settings }
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.{ ByteString, Timeout }
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import hmda.census.model.Msa
import hmda.model.fi.{ Submission, SubmissionId }
import hmda.model.institution.Institution
import hmda.persistence.HmdaSupervisor.{ FindProcessingActor, FindSubmissions }
import hmda.persistence.messages.commands.institutions.InstitutionCommands.GetInstitutionById
import hmda.persistence.messages.events.pubsub.PubSubEvents.SubmissionSignedPubSub
import hmda.persistence.model.HmdaActor
import hmda.persistence.model.HmdaSupervisorActor.FindActorByName
import hmda.persistence.processing.SubmissionManager.GetActorRef
import hmda.persistence.processing.{ PubSubTopics, SubmissionManager }
import hmda.query.repository.filing.LoanApplicationRegisterCassandraRepository
import hmda.validation.messages.ValidationStatsMessages.FindIrsStats
import hmda.validation.stats.SubmissionLarStats
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.ReportDetails
import hmda.parser.fi.lar.{ LarCsvParser, ModifiedLarCsvParser }
import hmda.persistence.institutions.SubmissionPersistence.GetLatestAcceptedSubmission
import hmda.persistence.institutions.{ InstitutionPersistence, SubmissionPersistence }
import hmda.persistence.messages.CommonMessages.Command
import hmda.persistence.messages.commands.publication.PublicationCommands._

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

case class GenerateDisclosureReportsWithMsa(institutionId: String, msa: Int) extends Command

object DisclosureReportPublisher {
  val name = "disclosure-report-publisher"
  def props(): Props = Props(new DisclosureReportPublisher)
}

class DisclosureReportPublisher extends HmdaActor with LoanApplicationRegisterCassandraRepository {

  val decider: Decider = { e =>
    repositoryLog.error("Unhandled error in stream", e)
    Supervision.Resume
  }

  override implicit def system: ActorSystem = context.system
  val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  override implicit def materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)

  val mediator = DistributedPubSub(context.system).mediator

  mediator ! Subscribe(PubSubTopics.submissionSigned, self)

  val duration = config.getInt("hmda.actor.timeout")
  implicit val timeout = Timeout(30.minutes)

  val accessKeyId = config.getString("hmda.publication.aws.access-key-id")
  val secretAccess = config.getString("hmda.publication.aws.secret-access-key ")
  val region = config.getString("hmda.publication.aws.region")
  val bucket = config.getString("hmda.publication.aws.public-bucket")
  val environment = config.getString("hmda.publication.aws.environment")

  val awsCredentials = new AWSStaticCredentialsProvider(
    new BasicAWSCredentials(accessKeyId, secretAccess)
  )
  val awsSettings = new S3Settings(MemoryBufferType, None, awsCredentials, region, false)
  val s3Client = new S3Client(awsSettings)

  val reports = List(
    D1, D2,
    D31, D32,
    D41, D42, D43, D44, D45, D46, D47,
    D51, D52, D53, D54, D56, D57,
    D71, D72, D73, D74, D75, D76, D77,
    D81, D82, D83, D84, D85, D86, D87,
    //D11_1, D11_2, D11_3, D11_4, D11_5, D11_6, D11_7, D11_8, D11_9, D11_10,
    D12_2,
    A1, A2, A3,
    A4,
    DiscB
  )

  val nationwideReports = List(A1W, A2W, A3W, A4W, DiscBW, DIRS)

  override def receive: Receive = {

    case SubscribeAck(Subscribe(PubSubTopics.submissionSigned, None, `self`)) =>
      log.info(s"${self.path} subscribed to ${PubSubTopics.submissionSigned}")

    case SubmissionSignedPubSub(submissionId) =>
    //self ! GenerateDisclosureReports(submissionId)

    case GenerateDisclosureReports2(institutionIds) =>
      institutionIds.foreach { inst =>
        Await.result(allReportsForInstitution(inst).map(s => Thread.sleep(2000)), 5.days)
      }

    case GenerateDisclosureReportsWithMsa(institutionId, msa) =>
      Await.result(allReportsForInstitution(institutionId, msa).map(s => Thread.sleep(2000)), 5.days)

    case PublishIndividualReport(institutionId, msa, report) =>
      publishIndividualReport(institutionId, msa, reportsByName(report))

    case _ => //do nothing
  }

  private def publishIndividualReport(institutionId: String, msa: Int, report: DisclosureReport) = {
    val larSeqF: Future[Seq[LoanApplicationRegister]] = s3Source(institutionId).runWith(Sink.seq)

    for {
      larSeq <- larSeqF
      institution <- getInstitution(institutionId)
    } yield {

      val larSource: Source[LoanApplicationRegister, NotUsed] = Source.fromIterator(() => larSeq.toIterator)
      val msaList = List(msa)
      val combinations = combine(msaList, List(report))

      val reportFlow = simpleReportFlow(larSource, institution, msaList)
      val publishFlow = s3Flow(institution)

      Source(combinations).via(reportFlow).via(publishFlow).runWith(Sink.ignore)
    }
  }

  private def allReportsForInstitution(institutionId: String, msa: Int = -1): Future[Unit] = {
    val larSeqF: Future[Seq[LoanApplicationRegister]] = s3Source(institutionId).runWith(Sink.seq)
    for {
      institution <- getInstitution(institutionId)
      subId <- getLatestAcceptedSubmissionId(institutionId)
      msas <- getMSAFromIRS(subId)
      larSeq <- larSeqF
    } yield {
      val msaSection = msas.slice(msas.indexOf(msa), msas.length)
      val stringMsa = if (msas.indexOf(msa) == -1 && msa != -1)
        s"\n\nMSA $msa was NOT FOUND\nRunning full list\nmsaList: $msas, submission: $subId\n"
      else s"msaList: $msaSection, submission: $subId\n"
      println(stringMsa)

      val larSource: Source[LoanApplicationRegister, NotUsed] = Source.fromIterator(() => larSeq.toIterator)
      if (msa == -1 && msas.indexOf(msa) != -1) {
        println(s"starting nationwide reports for $institutionId, beginning with msa ${msaSection.head}")
        Await.result(generateAndPublish(List(-1), nationwideReports, larSource, institution, msas.toList), 10.hours)
      }

      msaSection.foreach { msa: Int =>
        println(s"starting reports for $institutionId, msa $msa")
        Await.result(generateAndPublish(List(msa), reports, larSource, institution, msas.toList).map(s => Thread.sleep(1500)), 24.hours)
      }

    }
  }

  private def generateAndPublish(msaList: List[Int], reports: List[DisclosureReport], larSource: Source[LoanApplicationRegister, NotUsed], institution: Institution, allMSAs: List[Int]): Future[Done] = {
    val combinations = combine(msaList, reports)

    val reportFlow = simpleReportFlow(larSource, institution, allMSAs)
    val publishFlow = s3Flow(institution)

    Source(combinations).via(reportFlow).via(publishFlow).runWith(Sink.ignore)
  }

  def s3Flow(institution: Institution): Flow[DisclosureReportPayload, Future[MultipartUploadResult], NotUsed] =
    Flow[DisclosureReportPayload].map { payload =>
      val filePath = s"$environment/reports/disclosure/2017/${institution.id}/${payload.msa}/${payload.reportID}.txt"

      log.info(s"Publishing report. Institution: ${institution.id}, MSA: ${payload.msa}, Report #: ${payload.reportID}")

      Source.single(ByteString(payload.report)).runWith(s3Client.multipartUpload(bucket, filePath))
    }

  def simpleReportFlow(
    larSource: Source[LoanApplicationRegister, NotUsed],
    institution: Institution,
    msaList: List[Int]
  ): Flow[(Int, DisclosureReport), DisclosureReportPayload, NotUsed] =
    Flow[(Int, DisclosureReport)].mapAsync(1) {
      case (msa, report) =>
        report.generate(larSource, msa, institution, msaList)
    }

  def s3Source(institutionId: String): Source[LoanApplicationRegister, NotUsed] = {
    val sourceFileName = s"prod/modified-lar/2017/$institutionId.txt"
    s3Client
      .download(bucket, sourceFileName)
      .via(framing)
      .via(byteStringToLarFlow)
  }

  def framing: Flow[ByteString, ByteString, NotUsed] = {
    Framing.delimiter(ByteString("\n"), maximumFrameLength = 65536, allowTruncation = true)
  }

  val byteStringToLarFlow: Flow[ByteString, LoanApplicationRegister, NotUsed] =
    Flow[ByteString]
      .map(s => ModifiedLarCsvParser(s.utf8String) match {
        case Right(lar) => lar
      })

  /**
   * Returns all combinations of MSA and Disclosure Reports
   * Input:   List(407, 508) and List(D41, D42)
   * Returns: List((407, D41), (407, D42), (508, D41), (508, D42))
   */
  private def combine(a: List[Int], b: List[DisclosureReport]): List[(Int, DisclosureReport)] = {
    a.flatMap(msa => {
      List.fill(b.length)(msa).zip(b)
    })
  }

  val supervisor = system.actorSelection("/user/supervisor/singleton")

  private def getInstitution(institutionId: String): Future[Institution] = {
    val fInstitutionsActor = (supervisor ? FindActorByName(InstitutionPersistence.name)).mapTo[ActorRef]
    for {
      instPersistence <- fInstitutionsActor
      i <- (instPersistence ? GetInstitutionById(institutionId)).mapTo[Option[Institution]]
    } yield i.getOrElse(Institution.empty)
  }

  private def getLatestAcceptedSubmissionId(institutionId: String): Future[SubmissionId] = {
    val submissionPersistence = (supervisor ? FindSubmissions(SubmissionPersistence.name, institutionId, "2017")).mapTo[ActorRef]
    for {
      subPersistence <- submissionPersistence
      latestAccepted <- (subPersistence ? GetLatestAcceptedSubmission).mapTo[Option[Submission]]
    } yield latestAccepted.get.id
  }

  private def getMSAFromIRS(submissionId: SubmissionId): Future[Seq[Int]] = {
    for {
      manager <- (supervisor ? FindProcessingActor(SubmissionManager.name, submissionId)).mapTo[ActorRef]
      larStats <- (manager ? GetActorRef(SubmissionLarStats.name)).mapTo[ActorRef]
      stats <- (larStats ? FindIrsStats(submissionId)).mapTo[Seq[Msa]]
    } yield stats.filter(m => m.id != "NA").map(m => m.id.toInt)
  }

  private def reportsByName: Map[String, DisclosureReport] = Map(
    "1" -> D1,
    "11-1" -> D11_1,
    "11-10" -> D11_10,
    "11-2" -> D11_2,
    "11-3" -> D11_3,
    "11-4" -> D11_4,
    "11-5" -> D11_5,
    "11-6" -> D11_6,
    "11-7" -> D11_7,
    "11-8" -> D11_8,
    "11-9" -> D11_9,
    "12-2" -> D12_2,
    "2" -> D2,
    "3-1" -> D31,
    "3-2" -> D32,
    "4-1" -> D41,
    "4-2" -> D42,
    "4-3" -> D43,
    "4-4" -> D44,
    "4-5" -> D45,
    "4-6" -> D46,
    "4-7" -> D47,
    "5-1" -> D51,
    "5-2" -> D52,
    "5-3" -> D53,
    "5-4" -> D54,
    "5-6" -> D56,
    "5-7" -> D57,
    "7-1" -> D71,
    "7-2" -> D72,
    "7-3" -> D73,
    "7-4" -> D74,
    "7-5" -> D75,
    "7-6" -> D76,
    "7-7" -> D77,
    "8-1" -> D81,
    "8-2" -> D82,
    "8-3" -> D83,
    "8-4" -> D84,
    "8-5" -> D85,
    "8-6" -> D86,
    "8-7" -> D87,
    "A1" -> A1,
    "A2" -> A2,
    "A3" -> A3,
    "A4W" -> A4W,
    "B" -> DiscB,
    "A1W" -> A1W,
    "A2W" -> A2W,
    "A3W" -> A3W,
    "BW" -> DiscBW,
    "IRS" -> DIRS
  )

}
