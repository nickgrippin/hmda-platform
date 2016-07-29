package hmda.api.http

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._

trait HmdaCustomDirectives {

  def time: Directive0 = {
    val startTime = System.currentTimeMillis()

    mapResponse { response =>
      val endTime = System.currentTimeMillis()
      val responseTime = endTime - startTime
      println(s"$responseTime ms")
      response
    }

  }

}
