package hmda.publication

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.stream.ActorMaterializer
import hmda.persistence.model.HmdaActor
import hmda.publication.reports.disclosure.DisclosureReports

object HmdaPublication {
  case class GenerateDisclosureByMSAReports(respondentId: String, fipsCode: Int)
  def props(): Props = Props(new HmdaPublication)
  def createAggregateDisclosureReports(system: ActorSystem): ActorRef = {
    system.actorOf(HmdaPublication.props().withDispatcher("validation-dispatcher"), "hmda-aggregate-disclosure")
  }
}

class HmdaPublication extends HmdaActor {

  import HmdaPublication._

  implicit val system = context.system
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  override def receive: Receive = {
    case GenerateDisclosureByMSAReports(respId, fipsCode) =>
      val disclosureReports = new DisclosureReports(system, materializer)
      disclosureReports.generateReports(fipsCode, respId)
  }
}
