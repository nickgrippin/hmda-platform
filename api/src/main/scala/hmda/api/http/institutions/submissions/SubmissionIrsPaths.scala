package hmda.api.http.institutions.submissions

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.pattern.ask
import hmda.api.http.HmdaCustomDirectives
import hmda.api.model.{ Irs, IrsResponse }
import hmda.api.protocol.processing.MsaProtocol
import hmda.census.model.Msa
import hmda.model.fi.SubmissionId
import hmda.validation.ValidationStats.FindIrsStats

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait SubmissionIrsPaths
    extends HmdaCustomDirectives
    with RequestVerificationUtils
    with MsaProtocol {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  implicit val timeout: Timeout

  // institutions/<institutionId>/filings/<period>/submissions/<submissionId>/irs
  def submissionIrsPath(supervisor: ActorRef, querySupervisor: ActorRef, validationStats: ActorRef, institutionId: String)(implicit ec: ExecutionContext) =
    path("filings" / Segment / "submissions" / IntNumber / "irs") { (period, seqNr) =>
      timedGet { uri =>
        completeVerified(supervisor, querySupervisor, institutionId, period, seqNr, uri) {
          parameters('page.as[Int] ? 1) { (page: Int) =>
            onComplete(getMsa(validationStats, institutionId, period, seqNr)) {
              case Success(msaSeq) =>
                val irs: IrsResponse = Irs(msaSeq).paginatedResponse(page, uri.path.toString)
                complete(ToResponseMarshallable(irs))
              case Failure(e) => completeWithInternalError(uri, e)
            }
          }
        }
      }
    }

  // institutions/<institutionId>/filings/<period>/submissions/<submissionId>/irs/csv
  def submissionIrsCsvPath(supervisor: ActorRef, querySupervisor: ActorRef, validationStats: ActorRef, institutionId: String)(implicit ec: ExecutionContext) =
    path("filings" / Segment / "submissions" / IntNumber / "irs" / "csv") { (period, seqNr) =>
      timedGet { uri =>
        completeVerified(supervisor, querySupervisor, institutionId, period, seqNr, uri) {
          onComplete(getMsa(validationStats, institutionId, period, seqNr)) {
            case Success(msaSeq) =>
              val csv = Irs(msaSeq).toCsv
              complete(ToResponseMarshallable(csv))
            case Failure(e) => completeWithInternalError(uri, e)
          }
        }
      }
    }

  private def getMsa(validationStats: ActorRef, instId: String, period: String, seqNr: Int): Future[Seq[Msa]] = {
    val submissionId = SubmissionId(instId, period, seqNr)
    (validationStats ? FindIrsStats(submissionId)).mapTo[Seq[Msa]]
  }
}
