package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ReportTypeEnum
import hmda.model.publication.reports.ReportTypeEnum.Aggregate
import hmda.publication.model.LARTable
import hmda.publication.reports.{ AS, EC, MAT }
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

case class AggregateReportPayload(
  reportID: String,
  msa: String,
  report: String
)

trait AggregateReport {

  val reportType: ReportTypeEnum = Aggregate

  def generate[ec: EC, mat: MAT, as: AS](
    larSource: Source[LoanApplicationRegister, NotUsed],
    fipsCode: Int
  ): Future[AggregateReportPayload]

}

trait AggregateReportDB {

  val reportType: ReportTypeEnum = Aggregate

  def generate[ec: EC](
    larSource: TableQuery[LARTable],
    fipsCode: Int
  ): Future[AggregateReportPayload]

}
