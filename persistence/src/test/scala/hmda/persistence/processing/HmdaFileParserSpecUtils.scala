package hmda.persistence.processing

import akka.actor.ActorRef
import akka.testkit.TestProbe
import hmda.parser.fi.lar.LarCsvParser
import hmda.persistence.processing.HmdaFileParser.{ LarParsed, LarParsedErrors }

trait HmdaFileParserSpecUtils {

  def parseLars(actorRef: ActorRef, probe: TestProbe, xs: Array[String]): Unit = {
    val lars = xs.drop(1).map(line => LarCsvParser(line))
    lars.foreach {
      case Right(l) => probe.send(actorRef, LarParsed(l))
      case Left(errors) => probe.send(actorRef, LarParsedErrors(errors))
    }
  }

}
