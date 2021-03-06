package hmda.publication.reports.disclosure

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.{ LarGenerators, LoanApplicationRegister }
import hmda.model.publication.reports.ActionTakenTypeEnum._
import hmda.model.publication.reports.ApplicantIncomeEnum.LessThan50PercentOfMSAMedian
import hmda.model.publication.reports.{ EthnicityBorrowerCharacteristic, MSAReport, MinorityStatusBorrowerCharacteristic, RaceBorrowerCharacteristic }
import hmda.query.model.filing.LoanApplicationRegisterQuery
import hmda.query.repository.filing.LarConverter._
import org.scalacheck.Gen
import org.scalatest.{ AsyncWordSpec, BeforeAndAfterAll, MustMatchers }

import scala.concurrent.Future

class D52Spec extends AsyncWordSpec with MustMatchers with LarGenerators with BeforeAndAfterAll {

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  val respId = "98765"
  val fips = 18700 //Corvallis, OR
  def propType = Gen.oneOf(1, 2).sample.get

  val lars = lar100ListGen.sample.get.map { lar: LoanApplicationRegister =>
    val geo = lar.geography.copy(msa = fips.toString)
    val loan = lar.loan.copy(loanType = 1, propertyType = propType, purpose = 1)
    lar.copy(respondentId = respId, geography = geo, loan = loan)
  }

  val source: Source[LoanApplicationRegisterQuery, NotUsed] = Source
    .fromIterator(() => lars.toIterator)
    .map(lar => toLoanApplicationRegisterQuery(lar))

  val expectedDispositions = List(ApplicationReceived, LoansOriginated, ApprovedButNotAccepted, ApplicationsDenied, ApplicationsWithdrawn, ClosedForIncompleteness)

  "Generate a Disclosure 5-2 report" in {
    D52.generate(source, fips, respId, Future("Corvallis Test Bank")).map { result =>

      result.msa mustBe MSAReport("18700", "Corvallis, OR", "OR", "Oregon")
      result.table mustBe "5-2"
      result.respondentId mustBe "98765"
      result.institutionName mustBe "Corvallis Test Bank"
      result.applicantIncomes.size mustBe 5

      val lowestIncome = result.applicantIncomes.head
      lowestIncome.applicantIncome mustBe LessThan50PercentOfMSAMedian

      val races = lowestIncome.borrowerCharacteristics.head.asInstanceOf[RaceBorrowerCharacteristic].races
      races.size mustBe 8

      val ethnicities = lowestIncome.borrowerCharacteristics(1).asInstanceOf[EthnicityBorrowerCharacteristic].ethnicities
      ethnicities.size mustBe 4

      val minorityStatuses = lowestIncome.borrowerCharacteristics(2).asInstanceOf[MinorityStatusBorrowerCharacteristic].minoritystatus
      minorityStatuses.size mustBe 2

      races.head.dispositions.map(_.disposition) mustBe expectedDispositions
    }
  }

}
