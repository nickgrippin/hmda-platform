package hmda.publication.reports.util.db

import hmda.model.fi.lar.LoanApplicationRegister
import hmda.publication.DBUtils
import hmda.publication.model.LARTable
import hmda.publication.reports._

import scala.concurrent.Future
import scala.util.{ Success, Try }
import slick.jdbc.PostgresProfile.api._

object PricingDataUtilDB extends DBUtils {

  def pricingData[ec: EC](lars: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val rsNum = lars.filter(lar => lar.rateSpread =!= "NA")
    val aboveThresh = rsNum.filter(lar => lar.rateSpread.asColumnOf[Double] >= 1.5)
    for {
      noData <- pricingDisposition(lars.filter(lar => lar.rateSpread === "NA"), "No Reported Pricing Data")
      reported <- pricingDisposition(rsNum, "Reported Pricing Data")
      rs1_5 <- pricingDisposition(rsNum.filter(lar => rateSpreadBetween(lar, 1.5, 2)), "1.50 - 1.99")
      rs2_0 <- pricingDisposition(rsNum.filter(lar => rateSpreadBetween(lar, 2, 2.5)), "2.00 - 2.49")
      rs2_5 <- pricingDisposition(rsNum.filter(lar => rateSpreadBetween(lar, 2.5, 3)), "2.50 - 2.99")
      rs3 <- pricingDisposition(rsNum.filter(lar => rateSpreadBetween(lar, 3, 4)), "3.00 - 3.99")
      rs4 <- pricingDisposition(rsNum.filter(lar => rateSpreadBetween(lar, 4, 5)), "4.00 - 4.99")
      rs5 <- pricingDisposition(rsNum.filter(lar => rateSpreadBetween(lar, 5, Int.MaxValue)), "5 or more")
      mean <- reportedMean(aboveThresh)
      median <- reportedMedian(aboveThresh)
      hoepa <- pricingDisposition(lars.filter(_.hoepaStatus === 1), "HOEPA Loans")
    } yield {
      s"""
         |[
         |    $noData,
         |    $reported,
         |    $rs1_5,
         |    $rs2_0,
         |    $rs2_5,
         |    $rs3,
         |    $rs4,
         |    $rs5,
         |    $mean,
         |    $median,
         |    $hoepa
         |]
     """.stripMargin
    }
  }

  def rateSpreadBetween(lar: LARTable, lower: Double, upper: Double): Rep[Boolean] = {
    lar.rateSpread.asColumnOf[Double] >= lower && lar.rateSpread.asColumnOf[Double] < upper
  }

  private def pricingDisposition[ec: EC](filter: Query[LARTable, LARTable#TableElementType, Seq], title: String): Future[String] = {
    val loanCountF = count(filter)
    val valueSumF = sum(filter)
    for {
      count <- loanCountF
      totalValue <- valueSumF
    } yield {
      s"""
         |{
         |    "pricing": "$title",
         |    "count": $count,
         |    "value": $totalValue
         |}
       """.stripMargin
    }
  }

  private def reportedMean[ec: EC, mat: MAT, as: AS](lars: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val meanCount = calculateMean(lars, rateSpread)
    val meanValue = calculateMean(lars, loanAmount)

    Future.sequence(List(meanCount, meanValue)).map { results =>
      s"""
         |{
         |    "pricing": "Mean",
         |    "count": ${results.head},
         |    "value": ${results(1).toInt}
         |}
       """.stripMargin
    }
  }

  private def reportedMedian[ec: EC, mat: MAT, as: AS](lars: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val medianCount = calculateMedian(lars.filter(rateSpreadBetween(1.5, Int.MaxValue)), rateSpread)
    val medianValue = calculateMedian(lars.filter(rateSpreadBetween(1.5, Int.MaxValue)), loanAmount)

    Future.sequence(List(medianCount, medianValue)).map { results =>
      s"""
         |{
         |    "pricing": "Median",
         |    "count": ${results.head},
         |    "value": ${results(1).toInt}
         |}
       """.stripMargin
    }
  }

}
