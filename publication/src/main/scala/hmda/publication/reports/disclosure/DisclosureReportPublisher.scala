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
import hmda.persistence.messages.commands.publication.PublicationCommands._

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

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
  implicit val timeout = Timeout(duration.seconds)

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
    D11_1, D11_2, D11_3, D11_4, D11_5, D11_6, D11_7, D11_8, D11_9, D11_10,
    D12_2,
    A1, A2, A3,
    A4W,
    DiscB
  )

  val nationwideReports = List(A1W, A2W, A3W, DiscBW, DIRS)

  val institutionsToReport = List(
    "572495",
    "352772",
    "3874510",
    "3187630",
    "3228001",
    "14-1841762",
    "4320694",
    "4438423",
    "2213046",
    "636771",
    "412751",
    "4438441",
    "3955419",
    "3871359",
    "3877968",
    "541307",
    "3915431",
    "3868694",
    "3873447",
    "4184083",
    "3885806",
    "761806",
    "4877639",
    "4321963",
    "4346818",
    "3949203",
    "26-1193089",
    "1002878",
    "4183479",
    "3876390",
    "3877641",
    "1017939",
    "261146",
    "4323949",
    "4183732",
    "3837038",
    "3955361",
    "613307",
    "45-5510883",
    "99189",
    "3850082",
    "664756",
    "663245",
    "631570",
    "202907",
    "659855",
    "3837270",
    "3842368",
    "1223440",
    "4429908",
    "3885187",
    "2634191",
    "3904930",
    "724904",
    "656377",
    "3851557",
    "4533609",
    "288853",
    "4184766",
    "131490",
    "3885794",
    "3845734",
    "5023910",
    "858975",
    "3282852",
    "4878038",
    "2917317",
    "2925666",
    "4324982",
    "3592047",
    "734499",
    "601050",
    "4186863",
    "4325635",
    "3870419",
    "45-5523107",
    "3072606",
    "666581",
    "3848810",
    "3947843",
    "2855914",
    "652874",
    "694904",
    "4533618",
    "4323716",
    "463735",
    "3871966",
    "27-1438405",
    "4889892",
    "4437798",
    "3877566",
    "4533588",
    "601489",
    "3153130",
    "3881509",
    "3882805",
    "384278",
    "322793",
    "1007846",
    "4728919",
    "3876710",
    "3944226",
    "208244",
    "527954",
    "225698",
    "277697",
    "3877089",
    "245276",
    "945978",
    "3872039",
    "3837252",
    "3952191",
    "3880995",
    "509950",
    "3871920",
    "3844410",
    "937898",
    "2489805",
    "961624",
    "339072",
    "5027262",
    "4437789",
    "3845846",
    "3868827",
    "342634",
    "5134115",
    "3862021",
    "764030",
    "3875647",
    "723112",
    "3876354",
    "611198",
    "25085",
    "3955192",
    "46-3435079",
    "416245",
    "211271",
    "540926",
    "929352",
    "972590",
    "3913697",
    "3896071",
    "3913624",
    "2747587",
    "413208",
    "491224",
    "4437592",
    "3914135",
    "3954328",
    "5083268",
    "3897452"
  )

  override def receive: Receive = {

    case SubscribeAck(Subscribe(PubSubTopics.submissionSigned, None, `self`)) =>
      log.info(s"${self.path} subscribed to ${PubSubTopics.submissionSigned}")

    case SubmissionSignedPubSub(submissionId) =>
    //self ! GenerateDisclosureReports(submissionId)

    /*
    case GenerateDisclosureReports(submissionId) =>
      log.info(s"Generating disclosure reports for ${submissionId.institutionId}")
      // Ignore period and sequence number in submission id. Only use institutionID
      // the generateReports method uses period 2017 and the latest signed submission for this institution.
      generateReports(submissionId.institutionId)
      */

    case GenerateDisclosureNationwide(institutionId) =>
      //generateNationwideReports(institutionId)
      institutionsToReport.foreach { inst =>
        Await.result(allReportsForInstitution(inst).map(s => Thread.sleep(4000)), 24.hours)
      }

    case GenerateDisclosureForMSA(institutionId, msa) =>
      generateMSAReports(institutionId, msa)

    case PublishIndividualReport(institutionId, msa, report) =>
      publishIndividualReport(institutionId, msa, reportsByName(report))

    case GetReportDetails(institutionId) =>
      val sndr: ActorRef = sender()
      for {
        details <- getReportDetails(institutionId)
      } yield {
        sndr ! details
      }

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

  private def allReportsForInstitution(institutionId: String): Future[Unit] = {
    val larSeqF: Future[Seq[LoanApplicationRegister]] = s3Source(institutionId).runWith(Sink.seq)
    for {
      institution <- getInstitution(institutionId)
      subId <- getLatestAcceptedSubmissionId(institutionId)
      msas <- getMSAFromIRS(subId)
      larSeq <- larSeqF
    } yield {
      println(s"msaList: $msas, submission: $subId")
      val larSource: Source[LoanApplicationRegister, NotUsed] = Source.fromIterator(() => larSeq.toIterator)
      println(s"starting nationwide reports for $institutionId")
      Await.result(generateAndPublish(List(-1), nationwideReports, larSource, institution, msas.toList), 10.minutes)

      msas.foreach { msa: Int =>
        println(s"starting reports for $institutionId, msa $msa")
        Await.result(generateAndPublish(List(msa), reports, larSource, institution, msas.toList).map(s => Thread.sleep(1500)), 10.minutes)
      }

    }
  }

  private def generateAndPublish(msaList: List[Int], reports: List[DisclosureReport], larSource: Source[LoanApplicationRegister, NotUsed], institution: Institution, allMSAs: List[Int]): Future[Done] = {
    val combinations = combine(msaList, reports)

    val reportFlow = simpleReportFlow(larSource, institution, allMSAs)
    val publishFlow = s3Flow(institution)

    Source(combinations).via(reportFlow).via(publishFlow).runWith(Sink.ignore)
  }

  private def generateMSAReports(institutionId: String, msa: Int): Future[Unit] = {
    val larSeqF: Future[Seq[LoanApplicationRegister]] = s3Source(institutionId).runWith(Sink.seq)

    for {
      larSeq <- larSeqF
      institution <- getInstitution(institutionId)
    } yield {

      val larSource: Source[LoanApplicationRegister, NotUsed] = Source.fromIterator(() => larSeq.toIterator)
      val msaList = List(msa)
      val combinations = combine(msaList, reports)

      val reportFlow = simpleReportFlow(larSource, institution, msaList)
      val publishFlow = s3Flow(institution)

      Source(combinations).via(reportFlow).via(publishFlow).runWith(Sink.ignore)
    }
  }
  private def generateNationwideReports(institutionId: String): Future[Unit] = {
    val larSeqF: Future[Seq[LoanApplicationRegister]] = s3Source(institutionId).runWith(Sink.seq)
    for {
      institution <- getInstitution(institutionId)
      subId <- getLatestAcceptedSubmissionId(institutionId)
      msas <- getMSAFromIRS(subId)
      larSeq <- larSeqF
    } yield {

      val msaList = msas.toList
      val larSource: Source[LoanApplicationRegister, NotUsed] = Source.fromIterator(() => larSeq.toIterator)

      val combinations = combine(List(-1), nationwideReports)

      val reportFlow = simpleReportFlow(larSource, institution, msaList)
      val publishFlow = s3Flow(institution)

      Source(combinations).via(reportFlow).via(publishFlow).runWith(Sink.ignore)
    }
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
        //println(s"Generating report ${report.reportId} for msa $msa")
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
        case Right(lar) =>
          lar
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

  private def getReportDetails(institutionId: String): Future[ReportDetails] = {
    for {
      institution <- getInstitution(institutionId)
      subId <- getLatestAcceptedSubmissionId(institutionId)
      msas <- getMSAFromIRS(subId)
    } yield {
      val rd = ReportDetails(institution.respondent.name, subId, msas.size, msas)
      rd
    }
  }

  val supervisor = system.actorSelection("/user/supervisor/singleton")

  private def getInstitution(institutionId: String): Future[Institution] = {
    val fInstitutionsActor = (supervisor ? FindActorByName(InstitutionPersistence.name)).mapTo[ActorRef]
    for {
      instPersistence <- fInstitutionsActor
      i <- (instPersistence ? GetInstitutionById(institutionId)).mapTo[Option[Institution]]
    } yield {
      val inst = i.getOrElse(Institution.empty)
      inst
    }
  }

  private def getLatestAcceptedSubmissionId(institutionId: String): Future[SubmissionId] = {
    val submissionPersistence = (supervisor ? FindSubmissions(SubmissionPersistence.name, institutionId, "2017")).mapTo[ActorRef]
    for {
      subPersistence <- submissionPersistence
      latestAccepted <- (subPersistence ? GetLatestAcceptedSubmission).mapTo[Option[Submission]]
    } yield {
      val subId = latestAccepted.get.id
      subId
    }
  }

  private def getMSAFromIRS(submissionId: SubmissionId): Future[Seq[Int]] = {
    for {
      manager <- (supervisor ? FindProcessingActor(SubmissionManager.name, submissionId)).mapTo[ActorRef]
      larStats <- (manager ? GetActorRef(SubmissionLarStats.name)).mapTo[ActorRef]
      stats <- (larStats ? FindIrsStats(submissionId)).mapTo[Seq[Msa]]
    } yield {
      val msas = stats.filter(m => m.id != "NA").map(m => m.id.toInt)
      msas
    }
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
