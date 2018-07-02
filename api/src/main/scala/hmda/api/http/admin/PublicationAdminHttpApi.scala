package hmda.api.http.admin

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hmda.api.http.HmdaCustomDirectives
import hmda.api.protocol.processing.ApiErrorProtocol
import hmda.api.protocol.publication.ReportDetailsProtocol
import hmda.persistence.messages.commands.publication.PublicationCommands._
import hmda.persistence.messages.events.pubsub.PubSubEvents.FindDisclosurePublisher
import hmda.publication.reports.disclosure.GenerateDisclosureReportsWithMsa

import scala.util.{ Failure, Success }

trait PublicationAdminHttpApi extends HmdaCustomDirectives with ApiErrorProtocol with ReportDetailsProtocol {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val timeout: Timeout

  val log: LoggingAdapter

  def disclosureInstitutionsPath(publicationSupervisor: ActorRef) =
    path("disclosure" / "list") {
      extractExecutionContext { executor =>
        implicit val ec = executor

        timedPost { uri =>
          entity(as[List[String]]) { ids =>
            val message = for {
              p <- (publicationSupervisor ? FindDisclosurePublisher()).mapTo[ActorRef]
            } yield {
              p ! GenerateDisclosureReports2(ids)
            }

            onComplete(message) {
              case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
              case Failure(error) => completeWithInternalError(uri, error)
            }
          }

        }
      }
    }

  def disclosureInstitutionMsaPath(publicationSupervisor: ActorRef) =
    path("disclosure" / Segment / IntNumber) { (instId, msa) =>
      extractExecutionContext { executor =>
        implicit val ec = executor
        timedPost { uri =>
          val message = for {
            p <- (publicationSupervisor ? FindDisclosurePublisher()).mapTo[ActorRef]
          } yield {
            p ! GenerateDisclosureReportsWithMsa(instId, msa)
          }

          onComplete(message) {
            case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
            case Failure(error) =>
              completeWithInternalError(uri, error)
          }
        }
      }
    }

  def individualReportPath(publicationSupervisor: ActorRef) =
    path("disclosure" / Segment / Segment / Segment) { (instId, msa, report) =>
      extractExecutionContext { executor =>
        implicit val ec = executor
        timedPost { uri =>

          val reportMsa = if (msa == "nationwide") -1 else msa.toInt

          val message = for {
            p <- (publicationSupervisor ? FindDisclosurePublisher()).mapTo[ActorRef]
          } yield {
            p ! PublishIndividualReport(instId, reportMsa, report)
          }

          onComplete(message) {
            case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
            case Failure(error) =>
              completeWithInternalError(uri, error)
          }
        }
      }
    }

  def reportsListPath(publicationSupervisor: ActorRef) =
    path("disclosure" / Segment ) { (instId) =>
      extractExecutionContext { executor =>
        entity(as[List[String]]) { reports =>
          implicit val ec = executor
          timedPost { uri =>

            val message = for {
              p <- ( publicationSupervisor ? FindDisclosurePublisher() ).mapTo[ ActorRef ]
            } yield {
              p ! GenerateSeriesOfReports(instId, reports)
            }

            onComplete(message) {
              case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
              case Failure(error) =>
                completeWithInternalError(uri, error)
            }
          }
        }
      }
    }

  def publicationRoutes(supervisor: ActorRef, publicationSupervisor: ActorRef) =
    disclosureInstitutionsPath(publicationSupervisor) ~
      disclosureInstitutionMsaPath(publicationSupervisor) ~
      individualReportPath(publicationSupervisor) ~
      reportsListPath(publicationSupervisor)


  /*
def aggregateGenerationPath(supervisor: ActorRef, publicationSupervisor: ActorRef) =
  path("aggregate" / "2017") {
    extractExecutionContext { executor =>
      implicit val ec = executor
      timedPost { uri =>
        val publisherF = (publicationSupervisor ? FindAggregatePublisher()).mapTo[ActorRef]
        val msg = publisherF.map(_ ! GenerateAggregateReports())

        onComplete(msg) {
          case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
          case Failure(error) => completeWithInternalError(uri, error)
        }
      }
    }
  }

def publicationRoutes(supervisor: ActorRef, publicationSupervisor: ActorRef) =
  disclosureGenerationPath(supervisor, publicationSupervisor) ~ aggregateGenerationPath(supervisor, publicationSupervisor)
  */

}
