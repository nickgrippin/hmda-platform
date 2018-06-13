package hmda.publication.reports.util.db

import java.util.Calendar

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.census.model._
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ApplicantIncomeEnum._
import hmda.model.publication.reports._
import hmda.publication.DBUtils
import hmda.publication.model.{ LARTable, TractTable }
import hmda.publication.reports.util.DateUtil._
import hmda.publication.reports.util.DispositionType

import scala.concurrent.Future
import scala.util.Success
import slick.jdbc.PostgresProfile.api._

object ReportUtilDB extends DBUtils {

  def formattedCurrentDate: String = {
    formatDate(Calendar.getInstance().toInstant)
  }
  /*
  def msaReport(fipsCode: String): MSAReport = {
    CbsaLookup.values.find(x => x.cbsa == fipsCode || x.metroDiv == fipsCode) match {
      case Some(cbsa) =>
        val stateFips = cbsa.key.substring(0, 2)
        val state = StateAbrvLookup.values.find(s => s.state == stateFips).getOrElse(StateAbrv("", "", ""))
        MSAReport(fipsCode, CbsaLookup.nameFor(fipsCode), state.stateAbrv, state.stateName)
      case None => MSAReport("", "", "", "")
    }
  }*/

  def calculateMedianIncomeIntervals(fipsCode: Rep[Int]): Array[Rep[Double]] = {
    val msaIncome = MsaIncomeLookup.values.find(msa => msa.fips == fipsCode).getOrElse(MsaIncome())
    val medianIncome = msaIncome.income.toDouble / 1000
    val i50 = medianIncome * 0.5
    val i80 = medianIncome * 0.8
    val i100 = medianIncome
    val i120 = medianIncome * 1.2
    Array(i50, i80, i100, i120)
  }

  def larInIncomeInterval(lar: LARTable, larTable: TableQuery[LARTable], tracts: TableQuery[TractTable], applicantIncomeEnum: ApplicantIncomeEnum): Rep[Boolean] = {
    val joined = larTable joinLeft tracts on ((l, t) => { l.geographyMsa === t.msa && l.geographyTract === t.tract })
    applicantIncomeEnum match {
      case LessThan50PercentOfMSAMedian => joined.filter()
    }
  }

  def nationalLarsByIncomeInterval(larSource: Query[LARTable, LARTable#TableElementType, Seq], larTable: TableQuery[LARTable], tractSource: TableQuery[TractTable]): Map[ApplicantIncomeEnum, Query[LARTable, LARTable#TableElementType, Seq]] = {
    val lars50t = for {
      (c, s) <- larTable joinLeft tractSource on ((l, t) => { l.geographyState === t.state && l.geographyCounty === t.county && l.geographyTract === t.tract })
    } yield (c, s.map(_.msaMedIncome))

    lars50t

    val lars50 = larSource
      .filter(lar => joined.filter(l => l._1.applicantIncome.asColumnOf[Double] < l._2.msaMedIncome * 0.5))

    val lars50To79 = larSource
      .filter(lar => larInIncomeInterval(lar, larTable, tractSource, Between50And79PercentOfMSAMedian))

    val lars80To99 = larSource
      .filter(lar => larInIncomeInterval(lar, larTable, tractSource, Between80And99PercentOfMSAMedian))

    val lars100To120 = larSource
      .filter(lar => larInIncomeInterval(lar, larTable, tractSource, Between100And119PercentOfMSAMedian))

    val lars120 = larSource
      .filter(lar => larInIncomeInterval(lar, larTable, tractSource, GreaterThan120PercentOfMSAMedian))

    Map(
      LessThan50PercentOfMSAMedian -> lars50,
      Between50And79PercentOfMSAMedian -> lars50To79,
      Between80And99PercentOfMSAMedian -> lars80To99,
      Between100And119PercentOfMSAMedian -> lars100To120,
      GreaterThan120PercentOfMSAMedian -> lars120
    )
  }

  def larsByIncomeInterval(larSource: Source[LoanApplicationRegister, NotUsed], incomeIntervals: Array[Double]): Map[ApplicantIncomeEnum, Source[LoanApplicationRegister, NotUsed]] = {
    val lars50 = larSource
      .filter(lar => lar.applicant.income.toInt < incomeIntervals(0))

    val lars50To79 = larSource
      .filter(lar => lar.applicant.income.toInt >= incomeIntervals(0) && lar.applicant.income.toInt < incomeIntervals(1))

    val lars80To99 = larSource
      .filter(lar => lar.applicant.income.toInt >= incomeIntervals(1) && lar.applicant.income.toInt < incomeIntervals(2))

    val lars100To120 = larSource
      .filter(lar => lar.applicant.income.toInt >= incomeIntervals(2) && lar.applicant.income.toInt < incomeIntervals(3))

    val lars120 = larSource
      .filter(lar => lar.applicant.income.toInt >= incomeIntervals(3))

    Map(
      LessThan50PercentOfMSAMedian -> lars50,
      Between50And79PercentOfMSAMedian -> lars50To79,
      Between80And99PercentOfMSAMedian -> lars80To99,
      Between100And119PercentOfMSAMedian -> lars100To120,
      GreaterThan120PercentOfMSAMedian -> lars120
    )
  }
  /*
  def calculateYear[ec: EC, mat: MAT, as: AS](larSource: Source[LoanApplicationRegister, NotUsed]): Future[Int] = {
    collectHeadValue(larSource).map {
      case Success(lar) => lar.actionTakenDate.toString.substring(0, 4).toInt
      case _ => 0
    }
  }

  def calculateDispositions[ec: EC, mat: MAT, as: AS](
    larSource: Source[LoanApplicationRegister, NotUsed],
    dispositions: List[DispositionType]
  ): Future[List[ValueDisposition]] = {
    Future.sequence(dispositions.map(_.calculateValueDisposition(larSource)))
  }

  def calculatePercentageDispositions[ec: EC, mat: MAT, as: AS](
    larSource: Source[LoanApplicationRegister, NotUsed],
    dispositions: List[DispositionType],
    totalDisp: DispositionType
  ): Future[List[PercentageDisposition]] = {

    val calculatedDispositionsF: Future[List[PercentageDisposition]] =
      Future.sequence(dispositions.map(_.calculatePercentageDisposition(larSource)))

    for {
      calculatedDispositions <- calculatedDispositionsF
      totalCount <- count(larSource.filter(totalDisp.filter))
    } yield {

      val withPercentages: List[PercentageDisposition] = calculatedDispositions.map { d =>
        val percentage = if (d.count == 0) 0 else (d.count * 100 / totalCount)
        d.copy(percentage = percentage)
      }
      val totalPercent = if (totalCount == 0) 0 else 100
      val total = PercentageDisposition(totalDisp.value, totalCount, totalPercent)

      withPercentages :+ total
    }
  }*/

}
