package hmda.validation.rules

import akka.actor.ActorRef
import akka.cluster.singleton.{ ClusterSingletonProxy, ClusterSingletonProxySettings }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import hmda.validation.{ AS, EC }

import scala.concurrent.Future
import scala.concurrent.duration._

trait StatsLookup {
  val configuration = ConfigFactory.load()
  val duration = configuration.getInt("hmda.actor.timeout")
  val isClustered = configuration.getBoolean("hmda.isClustered")
  implicit val timeout = Timeout(duration.seconds)

  def validationStats(implicit system: AS[_], ec: EC[_]): Future[ActorRef] =
    if (isClustered) {
      Future(
        system.actorOf(
          ClusterSingletonProxy.props(
            singletonManagerPath = "/user/validation-stats",
            settings = ClusterSingletonProxySettings(system).withRole("persistence")
          )
        )
      )
    } else {
      system.actorSelection("/user/validation-stats").resolveOne()
    }

}
