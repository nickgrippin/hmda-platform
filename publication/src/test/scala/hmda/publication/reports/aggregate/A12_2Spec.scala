package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.{ LarGenerators, LoanApplicationRegister }
import org.scalacheck.Gen
import org.scalatest.{ AsyncWordSpec, BeforeAndAfterAll, MustMatchers }
import spray.json._

class A12_2Spec extends AsyncWordSpec with MustMatchers with LarGenerators with BeforeAndAfterAll {

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  val respId = "54345"
  val fips = 11540 // Appleton, WI
  val lars = Gen.listOfN(100, larWithValidGeoGen).sample.get.map { lar: LoanApplicationRegister =>
    val loan = lar.loan.copy(loanType = 1, purpose = 1, propertyType = 2, occupancy = 1)
    lar.copy(respondentId = respId, loan = loan, lienStatus = 1)
  }

  val source: Source[LoanApplicationRegister, NotUsed] = Source
    .fromIterator(() => lars.toIterator)

  val description = "Pricing information for conventional manufactured home-purchase loans, first lien, owner-occupied dwelling, by borrower or census tract characteristics"

  "Generate an Aggregate 12-2 report" in {
    A12_2.generate(source, fips).map { result =>
      result.report.parseJson.asJsObject.getFields("table", "description", "msa") match {
        case Seq(JsString(table), JsString(desc), msa) =>
          table mustBe "12-2"
          desc mustBe description
          msa.asJsObject.getFields("name") match {
            case Seq(JsString(msaName)) => msaName mustBe "Appleton, WI"
          }
      }
    }
  }

  "Include correct borrower Characteristics" in {
    A12_2.generate(source, fips).map { result =>
      result.report.parseJson.asJsObject.getFields("borrowerCharacteristics") match {

        case Seq(JsArray(characteristics)) =>
          characteristics must have size 5
          characteristics.head.asJsObject.getFields("characteristic", "races") match {

            case Seq(JsString(char), JsArray(races)) =>
              char mustBe "Race"
              races must have size 8
              races.head.asJsObject.getFields("race", "pricingInformation") match {

                case Seq(JsString(race), JsArray(pricing)) =>
                  race mustBe "American Indian/Alaska Native"
                  pricing must have size 11
              }
          }
      }
    }
  }

  "Include correct Census Tract Characteristics" in {
    N12_2.generate(source, fips).map { result =>
      result.report.parseJson.asJsObject.getFields("censusTractCharacteristics") match {

        case Seq(JsArray(characteristics)) =>
          characteristics must have size 2
          characteristics.head.asJsObject.getFields("characteristic", "compositions") match {

            case Seq(JsString(char), JsArray(races)) =>
              char mustBe "Racial/Ethnic Composition"
              races must have size 5
              races.head.asJsObject.getFields("composition", "pricingInformation") match {

                case Seq(JsString(race), JsArray(pricing)) =>
                  race mustBe "Less than 10% minority"
                  pricing must have size 11
              }
          }
      }
    }
  }

}
