package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ReportTypeEnum.Aggregate
import hmda.publication.model.LARTable
import hmda.publication.reports._
import hmda.publication.reports.util.ReportUtil._
import hmda.publication.reports.util.LoanTypeUtilDB._
import hmda.publication.reports.util.ReportsMetaDataLookup

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._

/*object AggregateA1 extends AggregateAX {
  val reportId = "A_A1"
  def filters(lar: LoanApplicationRegister): Boolean = {
    (1 to 4).contains(lar.loan.loanType) &&
      lar.loan.propertyType == 1
  }
}

object AggregateA2 extends AggregateAX {
  val reportId = "A_A2"
  def filters(lar: LoanApplicationRegister): Boolean = {
    (1 to 4).contains(lar.loan.loanType) &&
      lar.loan.propertyType == 2
  }
}

object AggregateA3 extends AggregateAX {
  val reportId = "A_A3"
  def filters(lar: LoanApplicationRegister): Boolean = {
    (1 to 4).contains(lar.loan.loanType) &&
      lar.loan.propertyType == 3
  }
}*/

object NationalAggregateA1 extends AggregateAX {
  val reportId = "N_A1"
  def filters(lar: TableQuery[LARTable]) = {
    lar.filter(l => (l.loanType inSet (1 to 4)) &&
      l.loanPropertyType === 1)
  }
}

object NationalAggregateA2 extends AggregateAX {
  val reportId = "N_A2"
  def filters(lar: TableQuery[LARTable]) = {
    lar.filter(l => (l.loanType inSet (1 to 4)) &&
      l.loanPropertyType === 2)
  }
}

object NationalAggregateA3 extends AggregateAX {
  val reportId = "N_A3"
  def filters(lar: TableQuery[LARTable]) = {
    lar.filter(l => (l.loanType inSet (1 to 4)) &&
      l.loanPropertyType === 3)
  }
}

trait AggregateAX extends AggregateReportDB {
  val reportId: String
  def filters(lar: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq]

  def geoFilter(fips: Int)(lar: LoanApplicationRegister): Boolean =
    lar.geography.msa != "NA" &&
      lar.geography.msa.toInt == fips

  def generate[ec: EC, mat: MAT, as: AS](
    larSource: TableQuery[LARTable],
    fipsCode: Int
  ): Future[AggregateReportPayload] = {

    val metaData = ReportsMetaDataLookup.values(reportId)

    val lars = filters(larSource)

    val reportDate = formattedCurrentDate

    for {
      received <- loanTypes(lars.filter(lar => lar.actionTakenType inSet (1 to 5)))
      originiated <- loanTypes(lars.filter(lar => lar.actionTakenType === 1))
      appNotAcc <- loanTypes(lars.filter(lar => lar.actionTakenType === 2))
      denied <- loanTypes(lars.filter(lar => lar.actionTakenType === 3))
      withdrawn <- loanTypes(lars.filter(lar => lar.actionTakenType === 4))
      closed <- loanTypes(lars.filter(lar => lar.actionTakenType === 5))
      preapproval <- loanTypes(lars.filter(lar => lar.actionTakenType === 1 && lar.preapprovals === 1))
      sold <- loanTypes(lars.filter(lar => lar.purchaserType inSet (1 to 9)))
    } yield {
      val report = s"""
                      |{
                      |    "table": "${metaData.reportTable}",
                      |    "type": "${metaData.reportType}",
                      |    "description": "${metaData.description}",
                      |    "year": "2017",
                      |    "reportDate": "$reportDate",
                      |    "dispositions": [
                      |        {
                      |            "disposition": "Applications Received",
                      |            "loanTypes": $received
                      |        },
                      |        {
                      |            "disposition": "Loans Originated",
                      |            "loanTypes": $originiated
                      |        },
                      |        {
                      |            "disposition": "Apps. Approved But Not Accepted",
                      |            "loanTypes": $appNotAcc
                      |        },
                      |        {
                      |            "disposition": "Applications Denied",
                      |            "loanTypes": $denied
                      |        },
                      |        {
                      |            "disposition": "Applications Withdrawn",
                      |            "loanTypes": $withdrawn
                      |        },
                      |        {
                      |            "disposition": "Files Closed For Incompleteness",
                      |            "loanTypes": $closed
                      |        },
                      |        {
                      |            "disposition": "Preapprovals Resulting in Originations",
                      |            "loanTypes": $preapproval
                      |        },
                      |        {
                      |            "disposition": "Loans Sold",
                      |            "loanTypes": $sold
                      |        }
                      |    ]
                      |}
       """.stripMargin

      val fipsString = if (metaData.reportType == Aggregate) fipsCode.toString else "nationwide"

      AggregateReportPayload(metaData.reportTable, fipsString, report)
    }
  }
}
