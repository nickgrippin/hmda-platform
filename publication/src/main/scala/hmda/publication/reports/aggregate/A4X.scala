package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ValueDisposition
import hmda.model.publication.reports.EthnicityEnum._
import hmda.model.publication.reports.GenderEnum.{ Female, JointGender, Male }
import hmda.model.publication.reports.ApplicantIncomeEnum._
import hmda.model.publication.reports.MinorityStatusEnum._
import hmda.model.publication.reports.RaceEnum._
import hmda.model.publication.reports.ReportTypeEnum.{ Aggregate, NationalAggregate }
import hmda.publication.model.{ LARTable, TractTable }
import hmda.publication.reports.{ AS, EC, MAT }
import hmda.publication.reports.util.db.EthnicityUtilDB._
import hmda.publication.reports.util.db.MinorityStatusUtilDB.filterMinorityStatus
import hmda.publication.reports.util.db.RaceUtilDB.filterRace
import hmda.publication.reports.util.db.ReportUtilDB._
import hmda.publication.reports.util.ReportsMetaDataLookup
import hmda.publication.reports.util.db.DispositionTypeDB._
import hmda.publication.reports.util.db.GenderUtilDB._

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._

/*
object A41 extends A4X {
  override val reportId: String = "A41"
  override def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    List(2, 3, 4).contains(loan.loanType) &&
      List(1, 2).contains(loan.propertyType) &&
      loan.purpose == 1
  }
}
object A42 extends A4X {
  override val reportId: String = "A42"
  override def filters(lar: LoanApplicationRegister): Boolean = {
    lar.loan.loanType == 1 &&
      (lar.loan.propertyType == 1 || lar.loan.propertyType == 2) &&
      (lar.loan.purpose == 1)
  }
}
object A43 extends A4X {
  override val reportId: String = "A43"
  override def filters(lar: LoanApplicationRegister): Boolean = {
    List(1, 2).contains(lar.loan.propertyType) &&
      lar.loan.purpose == 3
  }
}
object A44 extends A4X {
  override val reportId: String = "A44"
  override def filters(lar: LoanApplicationRegister): Boolean = {
    List(1, 2).contains(lar.loan.propertyType) && lar.loan.purpose == 2
  }
}
object A45 extends A4X {
  override val reportId: String = "A45"
  override def filters(lar: LoanApplicationRegister): Boolean =
    lar.loan.propertyType == 3
}
object A46 extends A4X {
  override val reportId: String = "A46"
  override def filters(lar: LoanApplicationRegister): Boolean = {
    lar.loan.occupancy == 2 &&
      List(1, 2, 3).contains(lar.loan.purpose) &&
      List(1, 2).contains(lar.loan.propertyType)
  }
}
object A47 extends A4X {
  override val reportId: String = "A47"
  override def filters(lar: LoanApplicationRegister): Boolean = {
    List(1, 2, 3).contains(lar.loan.purpose) &&
      lar.loan.propertyType == 2
  }
}*/

object N41 extends A4X {
  override val reportId: String = "N41"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => (lar.loanType inSet (2 to 4)) &&
      (lar.loanPropertyType inSet (1 to 2)) &&
      lar.loanPurpose === 1)
  }
}
object N42 extends A4X {
  override val reportId: String = "N42"
  override def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => lar.loanType === 1 &&
      lar.loanPurpose === 1 &&
      (lar.loanPropertyType inSet (1 to 2)))
  }
}
object N43 extends A4X {
  override val reportId: String = "N43"
  override def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => (lar.loanPropertyType inSet (1 to 2)) &&
      lar.loanPurpose === 3)
  }
}
object N44 extends A4X {
  override val reportId: String = "N44"
  override def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => (lar.loanPropertyType inSet (1 to 2)) &&
      lar.loanPurpose === 2)
  }
}
object N45 extends A4X {
  override val reportId: String = "N45"
  override def filters(lars: TableQuery[LARTable]) =
    lars.filter(lar => lar.loanPropertyType === 3)
}
object N46 extends A4X {
  override val reportId: String = "N46"
  override def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => lar.loanOccupancy === 2 &&
      (lar.loanPurpose inSet (1 to 3)) &&
      (lar.loanPropertyType inSet (1 to 2)))
  }
}
object N47 extends A4X {
  override val reportId: String = "N47"
  override def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => (lar.loanPurpose inSet (1 to 3)) &&
      lar.loanPropertyType === 2)
  }
}

trait A4X {
  val reportId: String
  def filters(lar: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq]

  val dispositions = List(ApplicationReceived, LoansOriginated, ApprovedButNotAccepted,
    ApplicationsDenied, ApplicationsWithdrawn, ClosedForIncompleteness)

  def generate[ec: EC](
    larSource: TableQuery[LARTable],
    tractSource: TableQuery[TractTable],
    fipsCode: Int
  ): Future[AggregateReportPayload] = {
    val metaData = ReportsMetaDataLookup.values(reportId)

    val filteredLars = filters(larSource)

    val reportLars = filteredLars.filter(lar => lar.applicantIncome =!= "NA")

    val joined = larSource joinLeft tractSource on ((l, t) => { l.geographyState === t.state && l.geographyCounty === t.county && l.geographyTract === t.tract })
    val incomeIntervals = nationalLarsByIncomeInterval(reportLars.filter(lar => lar.geographyMsa =!= "NA"), larSource, tractSource)

    val reportDate = formattedCurrentDate
    for {
      e1 <- dispositionsOutput(filterEthnicity(reportLars, HispanicOrLatino))
      e1g <- dispositionsByGender(filterEthnicity(reportLars, HispanicOrLatino))
      e2 <- dispositionsOutput(filterEthnicity(reportLars, NotHispanicOrLatino))
      e2g <- dispositionsByGender(filterEthnicity(reportLars, NotHispanicOrLatino))
      e3 <- dispositionsOutput(filterEthnicity(reportLars, JointEthnicity))
      e3g <- dispositionsByGender(filterEthnicity(reportLars, JointEthnicity))
      e4 <- dispositionsOutput(filterEthnicity(reportLars, NotAvailable))
      e4g <- dispositionsByGender(filterEthnicity(reportLars, NotAvailable))

      r1 <- dispositionsOutput(filterRace(reportLars, AmericanIndianOrAlaskaNative))
      r1g <- dispositionsByGender(filterRace(reportLars, AmericanIndianOrAlaskaNative))
      r2 <- dispositionsOutput(filterRace(reportLars, Asian))
      r2g <- dispositionsByGender(filterRace(reportLars, Asian))
      r3 <- dispositionsOutput(filterRace(reportLars, BlackOrAfricanAmerican))
      r3g <- dispositionsByGender(filterRace(reportLars, BlackOrAfricanAmerican))
      r4 <- dispositionsOutput(filterRace(reportLars, HawaiianOrPacific))
      r4g <- dispositionsByGender(filterRace(reportLars, HawaiianOrPacific))
      r5 <- dispositionsOutput(filterRace(reportLars, White))
      r5g <- dispositionsByGender(filterRace(reportLars, White))
      r6 <- dispositionsOutput(filterRace(reportLars, TwoOrMoreMinority))
      r6g <- dispositionsByGender(filterRace(reportLars, TwoOrMoreMinority))
      r7 <- dispositionsOutput(filterRace(reportLars, JointRace))
      r7g <- dispositionsByGender(filterRace(reportLars, JointRace))
      r8 <- dispositionsOutput(filterRace(reportLars, NotProvided))
      r8g <- dispositionsByGender(filterRace(reportLars, NotProvided))

      m1 <- dispositionsOutput(filterMinorityStatus(reportLars, WhiteNonHispanic))
      m1g <- dispositionsByGender(filterMinorityStatus(reportLars, WhiteNonHispanic))
      m2 <- dispositionsOutput(filterMinorityStatus(reportLars, OtherIncludingHispanic))
      m2g <- dispositionsByGender(filterMinorityStatus(reportLars, OtherIncludingHispanic))

      i1 <- dispositionsOutput(incomeIntervals(LessThan50PercentOfMSAMedian))
      i2 <- dispositionsOutput(incomeIntervals(Between50And79PercentOfMSAMedian))
      i3 <- dispositionsOutput(incomeIntervals(Between80And99PercentOfMSAMedian))
      i4 <- dispositionsOutput(incomeIntervals(Between100And119PercentOfMSAMedian))
      i5 <- dispositionsOutput(incomeIntervals(GreaterThan120PercentOfMSAMedian))
      i6 <- dispositionsOutput(filteredLars.filter(lar => lar.applicantIncome === "NA"))

      total <- dispositionsOutput(filteredLars)
    } yield {
      val report = s"""
         |{
         |    "table": "${metaData.reportTable}",
         |    "type": "${metaData.reportType}",
         |    "description": "${metaData.description}",
         |    "year": "2017",
         |    "reportDate": "$reportDate",
         |    "ethnicities": [
         |        {
         |            "ethnicity": "Hispanic or Latino",
         |            "dispositions": $e1,
         |            "genders": $e1g
         |        },
         |        {
         |            "ethnicity": "Not Hispanic or Latino",
         |            "dispositions": $e2,
         |            "genders": $e2g
         |        },
         |        {
         |            "ethnicity": "Joint (Hispanic or Latino/Not Hispanic or Latino)",
         |            "dispositions": $e3,
         |            "genders": $e3g
         |        },
         |        {
         |            "ethnicity": "Ethnicity Not Available",
         |            "dispositions": $e4,
         |            "genders": $e4g
         |        }
         |    ],
         |    "races": [
         |        {
         |            "race": "American Indian/Alaska Native",
         |            "dispositions": $r1,
         |            "genders": $r1g
         |        },
         |        {
         |            "race": "Asian",
         |            "dispositions": $r2,
         |            "genders": $r2g
         |        },
         |        {
         |            "race": "Black or African American",
         |            "dispositions": $r3,
         |            "genders": $r3g
         |        },
         |        {
         |            "race": "Native Hawaiian or Other Pacific Islander",
         |            "dispositions": $r4,
         |            "genders": $r4g
         |        },
         |        {
         |            "race": "White",
         |            "dispositions": $r5,
         |            "genders": $r5g
         |        },
         |        {
         |            "race": "2 or more minority races",
         |            "dispositions": $r6,
         |            "genders": $r6g
         |        },
         |        {
         |            "race": "Joint (White/Minority Race)",
         |            "dispositions": $r7,
         |            "genders": $r7g
         |        },
         |        {
         |            "race": "Race Not Available",
         |            "dispositions": $r8,
         |            "genders": $r8g
         |        }
         |    ],
         |    "minorityStatuses": [
         |        {
         |            "minorityStatus": "White Non-Hispanic",
         |            "dispositions": $m1,
         |            "genders": $m1g
         |        },
         |        {
         |            "minorityStatus": "Others, Including Hispanic",
         |            "dispositions": $m2,
         |            "genders": $m2g
         |        }
         |    ],
         |    "incomes": [
         |        {
         |            "income": "Less than 50% of MSA/MD median",
         |            "dispositions": $i1
         |        },
         |        {
         |            "income": "50-79% of MSA/MD median",
         |            "dispositions": $i2
         |        },
         |        {
         |            "income": "80-99% of MSA/MD median",
         |            "dispositions": $i3
         |        },
         |        {
         |            "income": "100-119% of MSA/MD median",
         |            "dispositions": $i4
         |        },
         |        {
         |            "income": "120% or more of MSA/MD median",
         |            "dispositions": $i5
         |        },
         |        {
         |            "income": "Income Not Available",
         |            "dispositions": $i6
         |        }
         |    ],
         |    "total": $total
         |}
         |
       """.stripMargin

      val fipsString = if (metaData.reportType == Aggregate) fipsCode.toString else "nationwide"

      AggregateReportPayload(reportId, fipsString, report)
    }
  }

  private def dispositionsOutput[ec: EC](query: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val calculatedDispositions: Future[List[ValueDisposition]] = Future.sequence(
      dispositions.map(_.calculateValueDisposition(query))
    )

    calculatedDispositions.map { list =>
      list.map(disp => disp.toJsonFormat).mkString("[", ",", "]")
    }
  }

  private def dispositionsByGender[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    for {
      male <- dispositionsOutput(filterGender(larSource, Male))
      female <- dispositionsOutput(filterGender(larSource, Female))
      joint <- dispositionsOutput(filterGender(larSource, JointGender))
    } yield {

      s"""
       |
       |[
       | {
       |     "gender": "Male",
       |     "dispositions": $male
       | },
       | {
       |     "gender": "Female",
       |     "dispositions": $female
       | },
       | {
       |     "gender": "Joint (Male/Female)",
       |     "dispositions": $joint
       | }
       |]
       |
     """.stripMargin

    }
  }

}
