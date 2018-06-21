package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.{ Sink, Source }
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ReportTypeEnum.Aggregate
import hmda.publication.model.LARTable
import hmda.publication.reports._
import hmda.publication.reports.util.db.ReportUtilDB._
import hmda.publication.reports.util.ReportsMetaDataLookup

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

/*
object AggregateB extends AggregateBX {
  override val reportId: String = "A_B"
}*/

object NationalAggregateB extends AggregateBX {
  override val reportId: String = "N_B"
}

trait AggregateBX {
  val reportId: String
  def filters(lars: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq] = {
    lars.filter(lar => lar.loanType === 1 && lar.loanOccupancy === 1)
  }

  def generate[ec: EC](
    larSource: TableQuery[LARTable],
    fipsCode: Int
  ): Future[AggregateReportPayload] = {

    val metaData = ReportsMetaDataLookup.values(reportId)

    val lars = filters(larSource)

    val singleFamily = lars.filter(lar => lar.loanPropertyType === 1)
    val manufactured = lars.filter(lar => lar.loanPropertyType === 2)

    val reportDate = formattedCurrentDate

    for {
      singleFamilyP1 <- purposes(singleFamily.filter(_.rateSpread === "NA"), countToString)
      singleFamilyP2 <- purposes(singleFamily.filter(_.rateSpread =!= "NA"), countToString)
      singleFamilyH1 <- purposes(singleFamily.filter(_.hoepaStatus === 1), countToString)
      singleFamilyH2 <- purposes(singleFamily.filter(_.hoepaStatus === 2), countToString)
      singleFamilyM1 <- purposes(singleFamily.filter(_.rateSpread =!= "NA"), meanToString)
      singleFamilyM2 <- purposes(singleFamily.filter(_.rateSpread =!= "NA"), medianToString)

      manufacturedP1 <- purposes(manufactured.filter(_.rateSpread === "NA"), countToString)
      manufacturedP2 <- purposes(manufactured.filter(_.rateSpread =!= "NA"), countToString)
      manufacturedH1 <- purposes(manufactured.filter(_.hoepaStatus === 1), countToString)
      manufacturedH2 <- purposes(manufactured.filter(_.hoepaStatus === 2), countToString)
      manufacturedM1 <- purposes(manufactured.filter(_.rateSpread =!= "NA"), meanToString)
      manufacturedM2 <- purposes(manufactured.filter(_.rateSpread =!= "NA"), medianToString)
    } yield {
      val report = s"""
       |{
       |    "table": "${metaData.reportTable}",
       |    "type": "${metaData.reportType}",
       |    "description": "${metaData.description}",
       |    "year": "2017",
       |    "reportDate": "$reportDate",
       |    "singleFamily": [
       |        {
       |            "characteristic": "Incidence of Pricing",
       |            "pricingInformation": [
       |                {
       |                    "pricing": "No pricing reported",
       |                    "purposes": $singleFamilyP1
       |                },
       |                {
       |                    "pricing": "Pricing reported",
       |                    "purposes": $singleFamilyP2
       |                },
       |                {
       |                    "pricing": "Mean (points above average prime offer rate: only includes loans with APR above the threshold)",
       |                    "purposes": $singleFamilyM2
       |                },
       |                {
       |                    "pricing": "Median (points above the average prime offer rate: only includes loans with APR above the threshold)",
       |                    "purposes": $singleFamilyM2
       |                }
       |            ]
       |        },
       |        {
       |            "characteristic": "HOEPA Status",
       |            "pricingInformation": [
       |                {
       |                    "pricing": "HOEPA loan",
       |                    "purposes": $singleFamilyH1
       |                },
       |                {
       |                    "pricing": "Not a HOEPA loan",
       |                    "purposes": $singleFamilyH2
       |                }
       |            ]
       |        }
       |    ],
       |    "manufactured": [
       |        {
       |            "characteristic": "Incidence of Pricing",
       |            "pricingInformation": [
       |                {
       |                    "pricing": "No pricing reported",
       |                    "purposes": $manufacturedP1
       |                },
       |                {
       |                    "pricing": "Pricing reported",
       |                    "purposes": $manufacturedP2
       |                },
       |                {
       |                    "pricing": "Mean (points above average prime offer rate: only includes loans with APR above the threshold)",
       |                    "purposes": $manufacturedM1
       |                },
       |                {
       |                    "pricing": "Median (points above the average prime offer rate: only includes loans with APR above the threshold)",
       |                    "purposes": $manufacturedM2
       |                }
       |            ]
       |        },
       |        {
       |            "characteristic": "HOEPA Status",
       |            "pricingInformation": [
       |                {
       |                    "pricing": "HOEPA loan",
       |                    "purposes": $manufacturedH1
       |                },
       |                {
       |                    "pricing": "Not a HOEPA loan",
       |                    "purposes": $manufacturedH2
       |                }
       |            ]
       |        }
       |    ]
       |}
       |
     """.stripMargin

      val fipsString = if (reportId == "DB") fipsCode.toString else "nationwide"

      AggregateReportPayload(metaData.reportTable, fipsString, report)
    }
  }

  private def purposes[ec: EC](lars: Query[LARTable, LARTable#TableElementType, Seq], f: Query[LARTable, LARTable#TableElementType, Seq] => Future[String]): Future[String] = {
    for {
      purchase <- lienDisposition("Home Purchase", lars.filter(lar => lar.loanPurpose === 1), f)
      refinance <- lienDisposition("Refinance", lars.filter(lar => lar.loanPurpose === 3), f)
      improvement <- lienDisposition("Home Improvement", lars.filter(lar => lar.loanPurpose === 2), f)
    } yield List(purchase, refinance, improvement).mkString("[", ",", "]")
  }

  private def lienDisposition[ec: EC](title: String, source: Query[LARTable, LARTable#TableElementType, Seq], f: Query[LARTable, LARTable#TableElementType, Seq] => Future[String]): Future[String] = {
    for {
      firstLien <- f(source.filter(lar => lar.lienStatus === 1))
      juniorLien <- f(source.filter(lar => lar.lienStatus === 2))
      noLien <- f(source.filter(lar => lar.lienStatus =!= 1 && lar.lienStatus =!= 2))
    } yield {
      s"""
         |{
         |    "purpose": "$title",
         |    "firstLienCount": $firstLien,
         |    "juniorLienCount": $juniorLien,
         |    "noLienCount": $noLien
         |}
       """
    }
  }

  private def countToString[ec: EC](source: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    count(source).map(_.toString)
  }

  private def medianToString[ec: EC](source: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    calculateMedian(source.filter(_.rateSpread.asColumnOf[Double] >= 1.5)).map(_.toString)
  }

  private def meanToString[ec: EC](source: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    calculateMean(source.filter(_.rateSpread.asColumnOf[Double] >= 1.5)).map(_.toString)
  }

}
