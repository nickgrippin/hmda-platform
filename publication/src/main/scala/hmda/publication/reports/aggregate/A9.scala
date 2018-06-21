package hmda.publication.reports.aggregate
import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.census.model.{ Tract, TractLookup }
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ValueDisposition
import hmda.publication.model.LARTable
import hmda.publication.reports.util.db.CensusTractUtilDB._
import hmda.publication.reports.util.db.DispositionTypeDB._
import hmda.publication.reports.util.ReportsMetaDataLookup
import hmda.publication.reports.util.ReportUtil._
import hmda.publication.reports.{ AS, EC, MAT }

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

/*
object A9 extends Aggregate9 {
  val reportId: String = "A9"
  def fipsString(fips: Int): String = fips.toString
  def msaString(fips: Int): String = s""""msa": ${msaReport(fips.toString).toJsonFormat},"""
  def msaTracts(fips: Int): Set[Tract] = TractLookup.values.filter(_.msa == fips.toString)
  def msaLars(larSource: Source[LoanApplicationRegister, NotUsed], fips: Int): Source[LoanApplicationRegister, NotUsed] =
    larSource.filter(_.geography.msa == fips)
}*/
object N9 extends Aggregate9 {
  val reportId: String = "N9"
  def fipsString(fips: Int): String = "nationwide"
  def msaString(fips: Int): String = ""
  def msaTracts(fips: Int): Set[Tract] = TractLookup.values
  def msaLars(larSource: Source[LoanApplicationRegister, NotUsed], fips: Int): Source[LoanApplicationRegister, NotUsed] =
    larSource.filter(_.geography.msa != "NA")
}

trait Aggregate9 {
  val reportId: String
  def fipsString(fips: Int): String
  def msaString(fips: Int): String
  def msaTracts(fips: Int): Set[Tract]
  def msaLars(larSource: Source[LoanApplicationRegister, NotUsed], fips: Int): Source[LoanApplicationRegister, NotUsed]

  val loanCategories = List(FHA, Conventional, Refinancings, HomeImprovementLoans,
    LoansForFiveOrMore, NonoccupantLoans, ManufacturedHomeDwellings)
  val dispositions = List(LoansOriginated, ApprovedButNotAccepted,
    ApplicationsDenied, ApplicationsWithdrawn, ClosedForIncompleteness)

  def filters(lars: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq] = lars.filter(_.actionTakenType inSet (1 to 5))

  def generate[ec: EC](
    larSource: TableQuery[LARTable],
    fipsCode: Int
  ): Future[AggregateReportPayload] = {

    val metaData = ReportsMetaDataLookup.values(reportId)

    val reportLars = filters(larSource)

    val reportDate = formattedCurrentDate
    val lars = reportLars.filter(_.geographyMsa =!= "NA")
    val larsUnknownMedianAgeInTract = filterUnknownMedianYearBuilt(reportLars)

    for {
      a1 <- loanCategoryDispositions(filterMedianYearHomesBuilt(lars, 2000, 2010))
      a2 <- loanCategoryDispositions(filterMedianYearHomesBuilt(lars, 1990, 2000))
      a3 <- loanCategoryDispositions(filterMedianYearHomesBuilt(lars, 1980, 1990))
      a4 <- loanCategoryDispositions(filterMedianYearHomesBuilt(lars, 1970, 1980))
      a5 <- loanCategoryDispositions(filterMedianYearHomesBuilt(lars, 0, 1980))
      a6 <- loanCategoryDispositions(larsUnknownMedianAgeInTract)

    } yield {
      val report =
        s"""
           |{
           |    "table": "${metaData.reportTable}",
           |    "type": "${metaData.reportType}",
           |    "description": "${metaData.description}",
           |    "year": "2017",
           |    "reportDate": "$reportDate",
           |    ${msaString(fipsCode)}
           |    "characteristic": "Census Tracts by Median Age of Homes",
           |    "medianAges": [
           |        {
           |            "medianAge": "2000 - 2010",
           |            "loanCategories": $a1
           |        },
           |        {
           |            "medianAge": "1990 - 1999",
           |            "loanCategories": $a2
           |        },
           |        {
           |            "medianAge": "1980 - 1989",
           |            "loanCategories": $a3
           |        },
           |        {
           |            "medianAge": "1970 - 1979",
           |            "loanCategories": $a4
           |        },
           |        {
           |            "medianAge": "1969 or Earlier",
           |            "loanCategories": $a5
           |        },
           |        {
           |            "medianAge": "Age Unknown",
           |            "loanCategories": $a6
           |        }
           |    ]
           |}
         """.stripMargin
      AggregateReportPayload(reportId, fipsString(fipsCode), report)
    }

  }

  private def loanCategoryDispositions[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val loanCategoryOutputs: Future[List[String]] = Future.sequence(
      loanCategories.map { loanCategory =>
        dispositionsOutput(larSource.filter(loanCategory.filter)).map { disp =>
          s"""
             |{
             |    "loanCategory": "${loanCategory.value}",
             |    "dispositions": $disp
             |}
           """.stripMargin
        }
      }
    )
    loanCategoryOutputs.map { list => list.mkString("[", ",", "]") }
  }

  private def dispositionsOutput[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val calculatedDispositions: Future[List[ValueDisposition]] = Future.sequence(
      dispositions.map(_.calculateValueDisposition(larSource))
    )

    calculatedDispositions.map(list => list.map(_.toJsonFormat).mkString("[", ",", "]"))
  }
}
