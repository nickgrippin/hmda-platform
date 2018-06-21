package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ReportTypeEnum.Aggregate
import hmda.publication.DBUtils
import hmda.publication.model.LARTable
import hmda.publication.reports._
import hmda.publication.reports.util.db.DispositionTypeDB
import hmda.publication.reports.util.db.DispositionTypeDB._
import hmda.publication.reports.util.db.PricingDataUtilDB._
import hmda.publication.reports.util.db.ReportUtilDB._
import hmda.publication.reports.util.{ DispositionType, ReportsMetaDataLookup }
import hmda.util.SourceUtils

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

/*
object A32 extends A32X {
  override val reportId = "A32"
}*/

object N32 extends A32X {
  override val reportId = "N32"
}

trait A32X extends DBUtils {
  val reportId = "D32"
  def filters(lars: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq] = {
    lars.filter(lar => {
      lar.actionTakenType === 1 &&
        (lar.purchaserType inSet (1 to 9)) &&
        (lar.lienStatus inSet (1 to 2))
    })
  }

  val dispositions = List(FannieMae, GinnieMae, FreddieMac,
    FarmerMac, PrivateSecuritization, CommercialBank,
    FinanceCompany, Affiliate, OtherPurchaser)

  def generate[ec: EC](
    larSource: TableQuery[LARTable],
    fipsCode: Int
  ): Future[AggregateReportPayload] = {

    val metaData = ReportsMetaDataLookup.values(reportId)

    val lars = filters(larSource)

    val reportDate = formattedCurrentDate

    val larsRsNa = lars.filter(_.rateSpread === "NA")
    val larsNonNa = lars.filter(_.rateSpread =!= "NA")

    for {
      p1 <- lienDispositions(larsRsNa)
      p2 <- lienDispositions(larsNonNa)

      rs1_5 <- lienDispositions(larsNonNa.filter(lar => rateSpreadBetween(lar, 1.5, 2)))
      rs2_0 <- lienDispositions(larsNonNa.filter(lar => rateSpreadBetween(lar, 2, 2.5)))
      rs2_5 <- lienDispositions(larsNonNa.filter(lar => rateSpreadBetween(lar, 2.5, 3)))
      rs3_0 <- lienDispositions(larsNonNa.filter(lar => rateSpreadBetween(lar, 3, 3.5)))
      rs3_5 <- lienDispositions(larsNonNa.filter(lar => rateSpreadBetween(lar, 3.5, 4.5)))
      rs4_5 <- lienDispositions(larsNonNa.filter(lar => rateSpreadBetween(lar, 4.5, 5.5)))
      rs5_5 <- lienDispositions(larsNonNa.filter(lar => rateSpreadBetween(lar, 5.5, 6.5)))
      rs6_5 <- lienDispositions(larsNonNa.filter(lar => rateSpreadBetween(lar, 6.5, Int.MaxValue)))

      mean <- meanDispositions(larsNonNa)
      median <- medianDispositions(larsNonNa)

      hoepa <- lienDispositions(lars.filter(_.hoepaStatus === 1))
    } yield {
      val report =
        s"""
           |{
           |    "table": "${metaData.reportTable}",
           |    "type": "${metaData.reportType}",
           |    "description": "${metaData.description}",
           |    "year": "2017",
           |    "reportDate": "$reportDate",
           |    "pricingInformation": [
           |        {
           |            "pricing": "No reported pricing data",
           |            "purchasers": $p1
           |        },
           |        {
           |            "pricing": "reported pricing data",
           |            "purchasers": $p2
           |        }
           |    ],
           |    "points": [
           |        {
           |            "pricing": "1.50 - 1.99",
           |            "purchasers": $rs1_5
           |        },
           |        {
           |            "pricing": "2.00 - 2.49",
           |            "purchasers": $rs2_0
           |        },
           |        {
           |            "pricing": "2.50 - 2.99",
           |            "purchasers": $rs2_5
           |        },
           |        {
           |            "pricing": "3.00 - 3.49",
           |            "purchasers": $rs3_0
           |        },
           |        {
           |            "pricing": "3.50 - 4.49",
           |            "purchasers": $rs3_5
           |        },
           |        {
           |            "pricing": "4.50 - 5.49",
           |            "purchasers": $rs4_5
           |        },
           |        {
           |            "pricing": "5.50 - 6.49",
           |            "purchasers": $rs5_5
           |        },
           |        {
           |            "pricing": "6.5 or more",
           |            "purchasers": $rs6_5
           |        },
           |        {
           |            "pricing": "Mean",
           |            "purchasers": $mean
           |        },
           |        {
           |            "pricing": "Median",
           |            "purchasers": $median
           |        }
           |    ],
           |    "hoepa": {
           |        "pricing": "HOEPA loans",
           |        "purchasers": $hoepa
           |    }
           |}
           |
       """.stripMargin

      val fipsString = if (metaData.reportType == Aggregate) fipsCode.toString else "nationwide"

      AggregateReportPayload(metaData.reportTable, fipsString, report)
    }
  }

  private def lienDispositions[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val calculatedDispositions: Future[List[String]] = Future.sequence(
      dispositions.map(lienDispositionOutput(_, larSource))
    )

    calculatedDispositions.map(list => list.mkString("[", ",", "]"))
  }
  private def lienDispositionOutput[ec: EC](disposition: DispositionTypeDB, larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val larsFiltered = larSource.filter(disposition.filter)

    val firstLienLars = larsFiltered.filter(_.lienStatus === 1)
    val flCountF = count(firstLienLars)
    val flTotalF = sumLoanAmount(firstLienLars)

    val juniorLienLars = larsFiltered.filter(_.lienStatus === 2)
    val jlCountF = count(juniorLienLars)
    val jlTotalF = sumLoanAmount(juniorLienLars)

    for {
      flCount <- flCountF
      flTotal <- flTotalF
      jlCount <- jlCountF
      jlTotal <- jlTotalF
    } yield {
      s"""
         |{
         |    "disposition": "${disposition.value}",
         |    "firstLienCount": $flCount,
         |    "firstLienValue": $flTotal,
         |    "juniorLienCount": $jlCount,
         |    "juniorLienValue": $jlTotal
         |}
        """
    }
  }

  private def meanDispositions[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val calculatedDispositions: Future[List[String]] = Future.sequence(
      dispositions.map(meanDisposition(_, larSource))
    )

    calculatedDispositions.map(list => list.mkString("[", ",", "]"))
  }
  private def meanDisposition[ec: EC](
    disposition: DispositionTypeDB,
    larSource: Query[LARTable, LARTable#TableElementType, Seq]
  ): Future[String] = {

    val larsFiltered = larSource.filter(disposition.filter).filter(_.rateSpread =!= "NA")

    val firstLienLars = larsFiltered.filter(_.lienStatus === 1)
    val flMeanF = calculateMean(firstLienLars)

    val juniorLienLars = larsFiltered.filter(_.lienStatus === 2)
    val jlMeanF = calculateMean(juniorLienLars)

    for {
      flMean <- flMeanF
      jlMean <- jlMeanF
    } yield {
      s"""
         |{
         |    "name": "${disposition.value}",
         |    "firstLienCount": $flMean,
         |    "firstLienValue": $flMean,
         |    "juniorLienCount": $jlMean,
         |    "juniorLienValue": $jlMean
         |}
       """.stripMargin
    }
  }

  private def medianDispositions[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val calculatedDispositions: Future[List[String]] = Future.sequence(
      dispositions.map(medianDisposition(_, larSource))
    )

    calculatedDispositions.map(list => list.mkString("[", ",", "]"))
  }
  private def medianDisposition[ec: EC](
    disposition: DispositionTypeDB,
    larSource: Query[LARTable, LARTable#TableElementType, Seq]
  ): Future[String] = {

    val larsFiltered = larSource.filter(disposition.filter).filter(_.rateSpread =!= "NA")

    val firstLienLars = larsFiltered.filter(_.lienStatus === 1)
    val flMedianF = calculateMedian(firstLienLars)

    val juniorLienLars = larsFiltered.filter(_.lienStatus === 2)
    val jlMedianF = calculateMedian(juniorLienLars)

    for {
      flMedian <- flMedianF
      jlMedian <- jlMedianF
    } yield {
      s"""
         |{
         |    "name": "${disposition.value}",
         |    "firstLienCount": $flMedian,
         |    "firstLienValue": $flMedian,
         |    "juniorLienCount": $jlMedian,
         |    "juniorLienValue": $jlMedian
         |}
       """.stripMargin
    }
  }
}
