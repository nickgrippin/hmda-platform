package hmda.publication.reports.aggregate

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.actor.{ ActorSystem, Props }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }
import akka.stream.Supervision._
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.alpakka.s3.{ MemoryBufferType, S3Settings }
import akka.stream.scaladsl.{ FileIO, Flow, Framing, Keep, Sink, Source, StreamConverters }
import akka.util.{ ByteString, Timeout }
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import hmda.persistence.model.HmdaActor
import akka.stream.alpakka.s3.javadsl.MultipartUploadResult
import com.typesafe.config.ConfigFactory
import hmda.census.model.MsaIncomeLookup
import hmda.model.ResourceUtils
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.parser.fi.lar.LarCsvParser
import hmda.persistence.messages.commands.publication.PublicationCommands.GenerateAggregateReports

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._

object AggregateReportPublisher {
  val name = "aggregate-report-publisher"
  def props(): Props = Props(new AggregateReportPublisher)
}

class AggregateReportPublisher extends HmdaActor with ResourceUtils {
  val config = ConfigFactory.load()

  val decider: Decider = { e =>
    Supervision.Resume
  }

  def framing: Flow[ByteString, ByteString, NotUsed] = {
    Framing.delimiter(ByteString("\n"), maximumFrameLength = 65536, allowTruncation = true)
  }

  implicit def system: ActorSystem = context.system
  val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit def materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)
  implicit def ec: ExecutionContext = ExecutionContext.fromExecutor(new java.util.concurrent.ForkJoinPool())
  val duration = config.getInt("hmda.actor.timeout")
  implicit val timeout = Timeout(duration.seconds)

  val accessKeyId = config.getString("hmda.publication.aws.access-key-id")
  val secretAccess = config.getString("hmda.publication.aws.secret-access-key ")
  val region = config.getString("hmda.publication.aws.region")
  val bucket = config.getString("hmda.publication.aws.public-bucket")
  val privateBucket = config.getString("hmda.publication.aws.private-bucket")
  val environment = config.getString("hmda.publication.aws.environment")

  val awsCredentials = new AWSStaticCredentialsProvider(
    new BasicAWSCredentials(accessKeyId, secretAccess)
  )
  val awsSettings = new S3Settings(MemoryBufferType, None, awsCredentials, region, false)
  val s3Client = new S3Client(awsSettings)

  val aggregateReports: List[AggregateReport] = List(
    A1, A2,
    AggregateA1, AggregateA2, AggregateA3,
    AggregateA4,
    AggregateB,
    A32,
    A41, A42, A43, A44, A45, A46, A47,
    A51, A52, A53, A54, A56, A57,
    A71, A72, A73, A74, A75, A76, A77,
    A81, A82, A83, A84, A85, A86, A87,
    A9,
    A11_1, A11_2, A11_3, A11_4, A11_5, A11_6, A11_7, A11_8, A11_9, A11_10,
    A12_1, A12_2
  )

  val nationalAggregateReports: List[AggregateReport] = List(
    NationalAggregateA1, NationalAggregateA2, NationalAggregateA3,
    NationalAggregateA4,
    NationalAggregateB,
    N32,
    N41, N42, N43, N44, N45, N46, N47,
    N51, N52, N53, N54, N56, N57,
    N71, N72, N73, N74, N75, N76, N77,
    N81, N82, N83, N84, N85, N86, N87,
    N9,
    N11_1, N11_2, N11_3, N11_4, N11_5, N11_6, N11_7, N11_8, N11_9, N11_10,
    N12_1, N12_2
  )

  override def receive: Receive = {

    case GenerateAggregateReports(start: Int) =>
      log.info(s"Generating aggregate reports for 2017 filing year starting at index $start")
      generateReports(start)

    case _ => //do nothing
  }

  val simpleReportFlow: Flow[(Int, AggregateReport), AggregateReportPayload, NotUsed] =
    Flow[(Int, AggregateReport)].mapAsyncUnordered(4) {
      case (msa, report) => {
        val source = s3Source(msa)
        log.info(s"Calling generate on ${report.getClass.getName} for MSA $msa")
        report.generate(source, msa)
      }
    }

  def simpleReportFlow2(source: Source[LoanApplicationRegister, NotUsed]): Flow[(Int, AggregateReport), AggregateReportPayload, NotUsed] =
    Flow[(Int, AggregateReport)].mapAsyncUnordered(3) {
      case (msa, report) => {
        log.info(s"Calling generate on ${report.getClass.getName} for MSA $msa")
        report.generate(source, msa)
      }
    }

  val byteStringToLarFlow: Flow[ByteString, LoanApplicationRegister, NotUsed] =
    Flow[ByteString]
      .map(s =>
        LarCsvParser(s.utf8String) match {
          case Right(lar) => lar
        })

  val s3Flow =
    Flow[AggregateReportPayload]
      .map(payload => {
        val filePath = s"prod/reports/aggregate/2017/${payload.msa}/${payload.reportID}.txt"
        log.info(s"Publishing Aggregate report. MSA: ${payload.msa}, Report #: ${payload.reportID}")

        Source.single(ByteString(payload.report))
          .runWith(s3Client.multipartUpload(bucket, filePath))
      })

  private def generateReports(start: Int) = {
    val msaList = MsaIncomeLookup.everyFips.toList

    val shortenedList = msaList.drop(start).filterNot(_ == -1)

    shortenedList.foreach(msa => {
      val reports = generateMSAReports2(msa)
      Await.result(reports, 24.hours)
    })
  }

  /**
   * Returns all combinations of MSA and Aggregate Reports
   * Input:   List(407, 508) and List(A41, A42)
   * Returns: List((407, A41), (407, A42), (508, A41), (508, A42))
   */
  private def combine(msas: List[Int], reports: List[AggregateReport]): List[(Int, AggregateReport)] = {
    msas.flatMap(msa => List.fill(reports.length)(msa).zip(reports))
  }

  private def msaToLarSource(msa: Int): Source[LoanApplicationRegister, NotUsed] = {
    val fileString = if (msa == -1) "2018-03-25_lar.txt" else s"$msa.txt"
    //val filePath = getClass.getClassLoader.getResourceAsStream(fileString)
    val filePath = getClass.getClassLoader.getResource(fileString).toURI

    //val larSourceTry = StreamConverters.fromInputStream(() => filePath)
    val larSourceTry = FileIO.fromPath(Paths.get(filePath))

    larSourceTry
      .via(framing)
      .viaMat(byteStringToLarFlow)(Keep.right)
  }

  def s3Source(msa: Int): Source[LoanApplicationRegister, NotUsed] = {
    val sourceFileName = s"dev/resources/aggregate/3-25/$msa.txt"
    s3Client
      .download(privateBucket, sourceFileName)
      .via(framing)
      .via(byteStringToLarFlow)
  }

  private def generateMSAReports2(msa: Int) = {
    val larSeqF: Future[Seq[LoanApplicationRegister]] = s3Source(msa).runWith(Sink.seq)

    for {
      larSeq <- larSeqF
      s <- getLarSeqFlow(larSeq, msa)
      t <- s
    } yield t
  }

  private def getLarSeqFlow(larSeq: Seq[LoanApplicationRegister], msa: Int) = {
    log.info(s"\n\nDOWNLOADED! $msa.txt     \nNumber of LARs is ${larSeq.length}\n")
    if(larSeq.length > 80000)
      log.info(s"\n   Too big, skipping\n")
    val larSource: Source[LoanApplicationRegister, NotUsed] = Source.fromIterator(() => larSeq.toIterator)
    val reportFlow = simpleReportFlow2(larSource)
    val combinations = if(larSeq.length > 80000) combine(List(msa), aggregateReports) else combine(List(), List())

    Source(combinations).via(reportFlow).via(s3Flow).runWith(Sink.last)
  }
}

