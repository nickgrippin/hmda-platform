package hmda.api.http.public

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import hmda.api.http.model.public.LarValidateRequest
import hmda.parser.filing.lar.LarCsvParser
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import hmda.api.http.codec.filing.LarCodec._
import hmda.api.http.directives.HmdaTimeDirectives
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import hmda.model.filing.lar.LoanApplicationRegister
import hmda.model.validation.LarValidationError
import hmda.validation.HmdaValidation
import hmda.validation.context.ValidationContext
import hmda.validation.engine.LarEngine._

import scala.concurrent.ExecutionContext

trait LarValidationHttpApi
    extends HmdaTimeDirectives
    with FilingValidationHttpApi {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  val log: LoggingAdapter
  implicit val ec: ExecutionContext
  implicit val timeout: Timeout

  //lar/parse
  val parseLarRoute =
    path("parse") {
      timedPost { _ =>
        entity(as[LarValidateRequest]) { req =>
          LarCsvParser(req.lar) match {
            case Right(lar) =>
              complete(ToResponseMarshallable(lar))
            case Left(errors) =>
              completeWithParsingErrors(errors)
          }
        }
      } ~
        timedOptions { _ =>
          complete("OPTIONS")
        }
    }

  //lar/validate
  val validateLarRoute =
    path("validate") {
      parameters('check.as[String] ? "all") { checkType =>
        timedPost { _ =>
          entity(as[LarValidateRequest]) { req =>
            LarCsvParser(req.lar) match {
              case Right(lar) => validate(lar, checkType)
              case Left(errors) =>
                completeWithParsingErrors(errors)
            }
          }
        }
      }
    }

  private def validate(lar: LoanApplicationRegister,
                       checkType: String): Route = {
    val ctx = ValidationContext(None)
    val validation: HmdaValidation[LoanApplicationRegister] = checkType match {
      case "all" => checkAll(lar, lar.loan.ULI, ctx, LarValidationError)
      case "syntactical" =>
        checkSyntactical(lar, lar.loan.ULI, ctx, LarValidationError)
      case "validity" => checkValidity(lar, lar.loan.ULI, LarValidationError)
      case "quality"  => checkQuality(lar, lar.loan.ULI)
    }

    val maybeErrors = validation.leftMap(xs => xs.toList).toEither

    maybeErrors match {
      case Right(l) => complete(l)
      case Left(errors) =>
        complete(ToResponseMarshallable(aggregateErrors(errors)))
    }
  }

  def larRoutes: Route = {
    handleRejections(corsRejectionHandler) {
      cors() {
        encodeResponse {
          pathPrefix("lar") {
            parseLarRoute ~ validateLarRoute
          }
        }
      }
    }
  }

}
