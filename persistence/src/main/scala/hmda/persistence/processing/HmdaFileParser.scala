package hmda.persistence.processing

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.persistence.PersistentActor
import akka.stream.{ ActorMaterializer, ClosedShape, FlowShape }
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source }
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.fi.ts.TransmittalSheet
import hmda.parser.fi.lar.LarCsvParser
import hmda.parser.fi.ts.TsCsvParser
import hmda.persistence.CommonMessages._
import hmda.persistence.LocalEventPublisher
import hmda.persistence.processing.HmdaRawFile.LineAdded
import hmda.persistence.processing.HmdaQuery._

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scalaz.StreamT.Done

object HmdaFileParser {

  val name = "HmdaFileParser"

  case class ReadHmdaRawFile(submissionId: String) extends Command
  case class TsParsed(ts: TransmittalSheet) extends Event
  case class TsParsedErrors(errors: List[String]) extends Event
  case class LarParsed(lar: LoanApplicationRegister) extends Event
  case class LarParsedErrors(errors: List[String]) extends Event

  case class CompleteParsing(submissionId: String) extends Command
  case class ParsingCompleted(submissionId: String) extends Event

  def props(id: String): Props = Props(new HmdaFileParser(id))

  def createHmdaFileParser(system: ActorSystem, submissionId: String): ActorRef = {
    system.actorOf(HmdaFileParser.props(submissionId))
  }

  case class HmdaFileParseState(size: Int = 0, parsingErrors: Seq[List[String]] = Nil) {
    def updated(event: Event): HmdaFileParseState = event match {
      case TsParsed(_) | LarParsed(_) =>
        HmdaFileParseState(size + 1, parsingErrors)
      case TsParsedErrors(errors) =>
        HmdaFileParseState(size, parsingErrors :+ errors)
      case LarParsedErrors(errors) =>
        HmdaFileParseState(size, parsingErrors :+ errors)
    }
  }

}

class HmdaFileParser(submissionId: String) extends PersistentActor with ActorLogging with LocalEventPublisher {

  import HmdaFileParser._

  implicit val system = context.system
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  var state = HmdaFileParseState()

  def updateState(event: Event): Unit = {
    state = state.updated(event)
  }

  override def preStart(): Unit = {
    log.debug(s"Parsing started for $submissionId")
  }

  override def postStop(): Unit = {
    log.debug(s"Parsing ended for $submissionId")
  }

  override def persistenceId: String = s"$name-$submissionId"

  override def receiveCommand: Receive = {

    case ReadHmdaRawFile(persistenceId) =>

      val flow = Flow.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
        import GraphDSL.Implicits._
        val parsedTs: Flow[Event, Event, NotUsed] =
          Flow[Event]
            .map { case LineAdded(_, data) => data }
            .take(1)
            .map(line => TsCsvParser(line))
            .map {
              case Left(errors) => TsParsedErrors(errors)
              case Right(ts) => TsParsed(ts)
            }

        val parsedLar: Flow[Event, Event, NotUsed] =
          Flow[Event]
            .map { case LineAdded(_, data) => data }
            .drop(1)
            .map(line => LarCsvParser(line))
            .map {
              case Left(errors) => LarParsedErrors(errors)
              case Right(lar) => LarParsed(lar)
            }

        val bcast = builder.add(Broadcast[Event](2))
        val merge = builder.add(Merge[Event](2))

        bcast ~> parsedLar ~> merge
        bcast ~> parsedTs ~> merge

        FlowShape(bcast.in, merge.out)
      })

      flow
        .runWith(events(persistenceId), Sink.actorRef(self, Shutdown))

    case tp @ TsParsed(ts) =>
      persist(tp) { e =>
        log.debug(s"Persisted: ${e.ts}")
        updateState(e)
      }

    case tsErr @ TsParsedErrors(errors) =>
      persist(tsErr) { e =>
        log.debug(s"Persisted: ${e.errors}")
        updateState(e)
      }

    case lp @ LarParsed(lar) =>
      persist(lp) { e =>
        log.debug(s"Persisted: ${e.lar}")
        updateState(e)
      }

    case larErr @ LarParsedErrors(errors) =>
      persist(larErr) { e =>
        log.debug(s"Persisted: ${e.errors}")
        updateState(e)
      }

    case GetState =>
      sender() ! state

    case Shutdown =>
      publishEvent(ParsingCompleted(submissionId))
      context stop self

  }

  override def receiveRecover: Receive = {
    case event: Event => updateState(event)
  }

}
