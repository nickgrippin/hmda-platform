package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.{ LarGenerators, LoanApplicationRegister }
import hmda.model.publication.reports.ApplicantIncomeEnum.LessThan50PercentOfMSAMedian
import hmda.model.publication.reports.DispositionEnum._
import hmda.model.publication.reports.{ EthnicityBorrowerCharacteristic, MSAReport, MinorityStatusBorrowerCharacteristic, RaceBorrowerCharacteristic }
import org.scalacheck.Gen
import org.scalatest.{ AsyncWordSpec, BeforeAndAfterAll, MustMatchers }

class A31Spec extends AsyncWordSpec with MustMatchers with LarGenerators with BeforeAndAfterAll {

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  val fips = 18700 //Corvallis, OR
  val lars = lar100ListGen.sample.get.map { lar: LoanApplicationRegister =>
    val geo = lar.geography.copy(msa = fips.toString)
    lar.copy(geography = geo)
  }

  val source: Source[LoanApplicationRegister, NotUsed] = Source
    .fromIterator(() => lars.toIterator)

  "Generate an Aggregate 3-1 report" in {
    A31.generate(source, fips).map { result =>
      result mustBe ""
    }
  }

}
