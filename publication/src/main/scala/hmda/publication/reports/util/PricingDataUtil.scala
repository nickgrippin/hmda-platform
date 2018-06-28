package hmda.publication.reports.util

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.publication.reports._
import hmda.util.SourceUtils

import scala.concurrent.Future
import scala.util.{ Success, Try }

object PricingDataUtil extends SourceUtils {

  def pricingData[ec: EC, mat: MAT, as: AS](lars: Source[LoanApplicationRegister, NotUsed]): Future[String] = {
    for {
      noData <- pricingDisposition(lars, _.rateSpread == "NA", "No Reported Pricing Data")
      reported <- pricingDisposition(lars, pricingDataReported, "Reported Pricing Data")
      rs1_5 <- pricingDisposition(lars, rateSpreadBetween(1.5, 2), "1.50 - 1.99")
      rs2_0 <- pricingDisposition(lars, rateSpreadBetween(2, 2.5), "2.00 - 2.49")
      rs2_5 <- pricingDisposition(lars, rateSpreadBetween(2.5, 3), "2.50 - 2.99")
      rs3 <- pricingDisposition(lars, rateSpreadBetween(3, 4), "3.00 - 3.99")
      rs4 <- pricingDisposition(lars, rateSpreadBetween(4, 5), "4.00 - 4.99")
      rs5 <- pricingDisposition(lars, rateSpreadBetween(5, Int.MaxValue), "5 or more")
      mean <- reportedMean(lars)
      median <- reportedMedian(lars)
      hoepa <- pricingDisposition(lars.filter(_.hoepaStatus == 1), (lar: LoanApplicationRegister) => true, "HOEPA Loans")
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

  def rateSpreadBetween(lower: Double, upper: Double)(lar: LoanApplicationRegister): Boolean = {
    Try(lar.rateSpread.toDouble) match {
      case Success(value) => value >= lower && value < upper
      case _ => false
    }
  }

  def pricingDataReported(lar: LoanApplicationRegister): Boolean = {
    rateSpreadBetween(Int.MinValue, Int.MaxValue)(lar)
  }

  private def pricingDisposition[ec: EC, mat: MAT, as: AS](larSource: Source[LoanApplicationRegister, NotUsed], filter: LoanApplicationRegister => Boolean, title: String): Future[String] = {
    val loansFiltered = larSource.filter(filter)
    val loanCountF = count(loansFiltered)
    val valueSumF = sum(loansFiltered, loanAmount)
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

  def loanAmount(lar: LoanApplicationRegister): Int = lar.loan.amount
  def rateSpread(lar: LoanApplicationRegister): Double =
    Try(lar.rateSpread.toDouble).getOrElse(0)
  def combined(lar: LoanApplicationRegister): (Int, Double) = (loanAmount(lar), rateSpread(lar))

  def reportedMean[ec: EC, mat: MAT, as: AS](lars: Source[LoanApplicationRegister, NotUsed]): Future[String] = {
    val loansFiltered = lars.filter(rateSpreadBetween(1.5, Int.MaxValue))

    for {
      count <- sum(loansFiltered, loanAmount)
      meanCount <- calculateMean(loansFiltered, rateSpread)
      weighted <- weightedSum(loansFiltered, loanAmount, count, rateSpread)
    } yield {
      val roundWeighted = BigDecimal(weighted * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      s"""
         |{
         |    "pricing": "Mean",
         |    "count": $meanCount,
         |    "value": $roundWeighted
         |}
       """.stripMargin
    }
  }

  def reportedMedian[ec: EC, mat: MAT, as: AS](lars: Source[LoanApplicationRegister, NotUsed]): Future[String] = {
    val medianCount = calculateMedian(lars.filter(rateSpreadBetween(1.5, Int.MaxValue)), rateSpread)
    val medianValue = calculateWeightedMedian(lars.filter(rateSpreadBetween(1.5, Int.MaxValue)), combined)

    Future.sequence(List(medianCount, medianValue)).map { results =>
      s"""
         |{
         |    "pricing": "Median",
         |    "count": ${results.head},
         |    "value": ${results(1)}
         |}
       """.stripMargin
    }
  }

}
