package hmda.query.view.institutions

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.persistence.{ RecoveryCompleted, SnapshotOffer }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import hmda.model.institution.Institution
import hmda.persistence.messages.CommonMessages.{ Command, Event, GetState }
import hmda.persistence.messages.events.institutions.InstitutionEvents._
import hmda.persistence.model.HmdaPersistentActor
import hmda.persistence.processing.HmdaQuery._
import hmda.query.model.ViewMessages.StreamCompleted
import hmda.persistence.PersistenceConfig._

object InstitutionView {

  val name = "institutions-view"

  case class GetInstitutionById(institutionId: String) extends Command
  case class GetInstitutionByRespondentId(respondentId: String) extends Command
  case class GetInstitutionsById(ids: List[String]) extends Command
  case class FindInstitutionByPeriodAndDomain(domain: String) extends Command

  def props(): Props = Props(new InstitutionView)

  def createInstitutionView(system: ActorSystem): ActorRef = {
    system.actorOf(InstitutionView.props().withDispatcher("query-dispatcher"), "institutions-view")
  }

  case class InstitutionViewState(institutions: Set[Institution] = Set.empty[Institution], seqNr: Long = 0L) {
    def updated(event: Event): InstitutionViewState = {
      event match {
        case InstitutionCreated(i) =>
          InstitutionViewState(institutions + i, seqNr + 1)
        case InstitutionModified(i) =>
          val others = institutions.filterNot(_.respondent.externalId == i.respondent.externalId)
          InstitutionViewState(others + i, seqNr + 1)
      }
    }
  }

}

class InstitutionView extends HmdaPersistentActor {

  import InstitutionView._

  var state = InstitutionViewState()

  var counter = 0

  val snapshotCounter = configuration.getInt("hmda.journal.snapshot.counter")

  override def persistenceId: String = name

  override def receiveCommand: Receive = {
    case GetInstitutionById(institutionId) =>
      val institution = state.institutions.find(i => i.id == institutionId).getOrElse(Institution.empty)
      sender() ! institution

    case GetInstitutionByRespondentId(respondentId) =>
      val institution = state.institutions.find { i =>
        i.respondent.externalId.value == respondentId
      }.getOrElse(Institution.empty)
      sender() ! institution

    case GetInstitutionsById(ids) =>
      val institutions = state.institutions.filter(i => ids.contains(i.id))
      sender() ! institutions

    case EventWithSeqNr(_, event) =>
      if (counter >= snapshotCounter) {
        counter = 0
        saveSnapshot(state)
      }
      event match {
        case InstitutionCreated(_) =>
          updateState(event)
        case InstitutionModified(_) =>
          updateState(event)
      }

    case GetState =>
      sender() ! state.institutions

    case FindInstitutionByPeriodAndDomain(domain) =>
      if (domain.isEmpty) {
        sender() ! Set.empty[Institution]
      } else {
        sender() ! state.institutions.filter(i => i.emailDomains.map(e => extractDomain(e)).contains(domain.toLowerCase))
      }
  }

  override def receiveRecover: Receive = super.receiveRecover orElse {
    case SnapshotOffer(_, s: InstitutionViewState) => state = s
    case RecoveryCompleted => recoveryCompleted()
  }

  def recoveryCompleted(): Unit = {
    implicit val materializer = ActorMaterializer()
    eventsWithSequenceNumber("institutions", state.seqNr + 1, Long.MaxValue)
      .runWith(Sink.actorRef(self, StreamCompleted))
  }

  override def updateState(event: Event): Unit = {
    state = state.updated(event)
    counter += 1
  }

  private def extractDomain(email: String): String = {
    val parts = email.split("@")
    if (parts.length > 1)
      parts(1).toLowerCase
    else
      parts(0).toLowerCase
  }

}
