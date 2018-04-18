package hmda.publication.reports.aggregate

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.actor.{ ActorSystem, Props }
import akka.http.javadsl.model.Uri
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
import hmda.publication.reports.disclosure.A3

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

  val completedMsas = List(10180, 10380, 10420, 10500, 10540, 10580, 10740, 10780, 10900, 11020, 11100, 11180, 11244, 11260, 11460, 11500, 11540, 11640, 11700, 12020, 12060, 12100, 12220, 12260, 12420, 12540, 12580, 12620, 12700, 12940, 12980, 13020, 13140, 13220, 13380, 13460, 13740, 13780, 13820, 13900, 13980, 14010, 14020, 14100, 14260, 14454, 14500, 14540, 14740, 14860, 15180, 15260, 15380, 15500, 15540, 15680, 15764, 15804, 15940, 15980, 16020, 16060, 16180, 16220, 16300, 16540, 16580, 16620, 16700, 16740, 16820, 16860, 16940, 16974, 17020, 17140, 17300, 17420, 17460, 17660, 17780, 17820, 17860, 17900, 17980, 18020, 18140, 18580, 18700, 18880, 19060, 19124, 19140, 19180, 19300, 19340, 19380, 19460, 19500, 19660, 19740, 19780, 19804, 20020, 20100, 20220, 20260, 20500, 20524, 20700, 20740, 20940, 20994, 21060, 21140, 21300, 21340, 21420, 21500, 21660, 21780, 21820, 22020, 22140, 22180, 22220, 22380, 22420, 22500, 22520, 22540, 22660, 22744, 22900, 23060, 23104, 23420, 23460, 23540, 23580, 23844, 23900, 24020, 24140, 24220, 24260, 24300, 24340, 24420, 24500, 24540, 24580, 24660, 24780, 24860, 25020, 25060, 25180, 25220, 25260, 25420, 25500, 25540, 25620, 25860, 25940, 25980, 26140, 26300, 26380, 26420, 26580, 26620, 26820, 26900, 26980, 27060, 27100, 27140, 27180, 27260, 27340, 27500, 27740, 27780, 27860, 27900, 27980, 28020, 28100, 28140, 28420, 28660, 28700, 28740, 28940, 29020, 29100, 29180, 29200, 29340, 29404, 29420, 29460, 29540, 29620, 29700, 29740, 29820, 29940, 30020, 30140, 30300, 30340, 30460, 30620, 30700, 30780, 30860, 30980, 31020, 31084, 31140, 31180, 31340, 31420, 31460, 31540, 31700, 31740, 31860, 31900, 32420, 32580, 32780, 32820, 32900, 33124, 33140, 33220, 33260, 33340, 33460, 33540, 33660, 33700, 33740, 33780, 33860, 33874, 34060, 34100, 34580, 34620, 34740, 34820, 34900, 34940, 34980, 35004, 35084, 35100, 35300, 35380, 35660, 35840, 35980, 36100, 36140, 36220, 36420, 36500, 36740, 36780, 36980, 37100, 37340, 37460, 37620, 37860, 37964, 38220, 38300, 38340, 38540, 38860, 38940, 39140, 39300, 39380, 39460, 39540, 39580, 39660, 39740, 39820, 39900, 40140, 40220, 40340, 40380, 40420, 40484, 40580, 40660, 40900, 40980, 41060, 41100, 41140, 41420, 41500, 41540, 41620, 41660, 41740, 41884, 41900, 41940, 42020, 42034, 42100, 42140, 42200, 42220, 42340, 42540, 42644, 42680, 42700, 43100, 43300, 43420, 43524, 43580, 43620, 43780, 43900, 44060, 44100, 44140, 44220, 44300, 44700, 44940, 45060, 45104, 45220, 45300, 45540, 45780, 45940, 46060, 46340, 46520, 46540, 46660, 46700, 47220, 47300, 47380, 47460, 47580, 47664, 47940, 48060, 48140, 48300, 48424, 48540, 48660, 48700, 48864, 48900, 49020, 49180, 49420, 49620, 49660, 49700, 49740)
  // 49
  val aggregateReports: List[AggregateReport] = List(
    AI,
    //A1,
    A2,
    AggregateA1, AggregateA2, AggregateA3,
    AggregateA4,
    AggregateB,
    A31,
    A32,
    A41, A42, A43, A44, A45, A46, A47,
    A51, A52, A53, A54, A56, A57,
    A71, A72, A73, A74, A75, A76, A77,
    A81, A82, A83, A84, A85, A86, A87,
    A9,
    A11_1, A11_2, A11_3, A11_4, A11_5, A11_6, A11_7, A11_8, A11_9, A11_10,
    A12_1, A12_2
  )

  val reportMap: Map[Int, List[AggregateReport]] = Map(
    //47894 -> List(A1),
    35614 -> List( /*A9,*/ A1)
  //27620 -> List(A1)
  )

  // 46
  val nationalAggregateReports: List[AggregateReport] = List(
    NationalAggregateA1, NationalAggregateA2, NationalAggregateA3,
    NationalAggregateA4,
    NationalAggregateB,
    N31,
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
    Flow[(Int, AggregateReport)].mapAsyncUnordered(2) {
      case (msa, report) => {
        val source = s3Source(msa)
        log.info(s"Calling generate on ${report.getClass.getName} for MSA $msa")
        report.generate(source, msa)
      }
    }

  def simpleReportFlow2(source: Source[LoanApplicationRegister, NotUsed]): Flow[(Int, AggregateReport), AggregateReportPayload, NotUsed] =
    Flow[(Int, AggregateReport)].map {
      case (msa, report) => {
        log.info(s"Calling generate on ${report.getClass.getName} for MSA $msa")
        Await.result(report.generate(source, msa), 24.hours)
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

    val shortenedList = msaList.drop(start).filterNot(_ == -1).filterNot(completedMsas.contains(_))

    reportMap.keys.foreach(msa => {
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
      t <- s.getOrElse(Future.apply(MultipartUploadResult(Uri.EMPTY, "", "", "")))
    } yield t
  }

  private def getLarSeqFlow(larSeq: Seq[LoanApplicationRegister], msa: Int) = {
    log.info(s"\n\nDOWNLOADED! $msa.txt     \nNumber of LARs is ${larSeq.length}\n")
    val larSource: Source[LoanApplicationRegister, NotUsed] = Source.fromIterator(() => larSeq.toIterator)
    val reportFlow = simpleReportFlow2(larSource)
    val combinations = combine(List(msa), reportMap.get(msa).get)

    Source(combinations).via(reportFlow).via(s3Flow).runWith(Sink.lastOption)
  }
}

