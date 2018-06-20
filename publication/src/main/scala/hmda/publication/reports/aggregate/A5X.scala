package hmda.publication.reports.aggregate

import hmda.publication.reports.util.ReportsMetaDataLookup
import hmda.publication.reports.util.db.ReportUtilDB._
import hmda.model.publication.reports.ApplicantIncomeEnum._
import hmda.model.publication.reports.EthnicityEnum.{ HispanicOrLatino, JointEthnicity, NotAvailable, NotHispanicOrLatino }
import hmda.model.publication.reports.MinorityStatusEnum.{ OtherIncludingHispanic, WhiteNonHispanic }
import hmda.model.publication.reports.RaceEnum._
import hmda.model.publication.reports.ReportTypeEnum.{ Aggregate, NationalAggregate }
import hmda.model.publication.reports.ValueDisposition
import hmda.publication.model.LARTable
import hmda.publication.reports._
import hmda.publication.reports.util.db.DispositionTypeDB._
import hmda.publication.reports.util.db.EthnicityUtilDB.filterEthnicity
import hmda.publication.reports.util.db.MinorityStatusUtilDB.filterMinorityStatus
import hmda.publication.reports.util.db.RaceUtilDB.filterRace

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

/*
object A51 extends A5X {
  val reportId = "A51"
  def filters(lar: LoanApplicationRegister): Boolean = {
    (lar.loan.loanType == 2 || lar.loan.loanType == 3 || lar.loan.loanType == 4) &&
      (lar.loan.propertyType == 1 || lar.loan.propertyType == 2) &&
      (lar.loan.purpose == 1)
  }
}

object A52 extends A5X {
  val reportId = "A52"
  def filters(lar: LoanApplicationRegister): Boolean = {
    (lar.loan.loanType == 1) &&
      (lar.loan.propertyType == 1 || lar.loan.propertyType == 2) &&
      (lar.loan.purpose == 1)
  }
}

object A53 extends A5X {
  val reportId = "A53"
  def filters(lar: LoanApplicationRegister): Boolean = {
    (lar.loan.propertyType == 1 || lar.loan.propertyType == 2) &&
      (lar.loan.purpose == 3)
  }
}

object A54 extends A5X {
  val reportId = "A54"
  def filters(lar: LoanApplicationRegister): Boolean = {
    (lar.loan.propertyType == 1 || lar.loan.propertyType == 2) &&
      (lar.loan.purpose == 2)
  }
}

object A56 extends A5X {
  val reportId = "A56"
  def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    loan.occupancy == 2 &&
      (loan.propertyType == 1 || loan.propertyType == 2) &&
      (loan.purpose == 1 || loan.purpose == 2 || loan.purpose == 3)
  }
}

object A57 extends A5X {
  val reportId = "A57"
  def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    loan.propertyType == 2 &&
      (loan.purpose == 1 || loan.purpose == 2 || loan.purpose == 3)
  }
}*/

object N51 extends A5X {
  val reportId = "N51"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      (lar.loanType === 2 || lar.loanType === 3 || lar.loanType === 4) &&
        (lar.loanPropertyType === 1 || lar.loanPropertyType === 2) &&
        (lar.loanPurpose === 1)
    })
  }
}

object N52 extends A5X {
  val reportId = "N52"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      (lar.loanType === 1) &&
        (lar.loanPropertyType === 1 || lar.loanPropertyType === 2) &&
        (lar.loanPurpose === 1)
    })
  }
}

object N53 extends A5X {
  val reportId = "N53"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      (lar.loanPropertyType === 1 || lar.loanPropertyType === 2) &&
        (lar.loanPurpose === 3)
    })
  }
}

object N54 extends A5X {
  val reportId = "N54"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      (lar.loanPropertyType === 1 || lar.loanPropertyType === 2) &&
        (lar.loanPurpose === 2)
    })
  }
}

object N56 extends A5X {
  val reportId = "N56"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      lar.loanOccupancy === 2 &&
        (lar.loanPropertyType === 1 || lar.loanPropertyType === 2) &&
        (lar.loanPurpose === 1 || lar.loanPurpose === 2 || lar.loanPurpose === 3)
    })
  }
}

object N57 extends A5X {
  val reportId = "N57"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      lar.loanPropertyType === 2 &&
        (lar.loanPurpose === 1 || lar.loanPurpose === 2 || lar.loanPurpose === 3)
    })
  }
}

trait A5X {
  val reportId: String
  def filters(lar: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq]
  val dispositions = List(ApplicationReceived, LoansOriginated, ApprovedButNotAccepted,
    ApplicationsDenied, ApplicationsWithdrawn, ClosedForIncompleteness)

  def generate[ec: EC](
    larSource: TableQuery[LARTable],
    fipsCode: Int
  ): Future[AggregateReportPayload] = {

    val metaData = ReportsMetaDataLookup.values(reportId)

    val reportLars = filters(larSource)
      .filter(lar => lar.geographyMsa =!= "NA")
      .filter(lar => lar.applicantIncome =!= "NA")
      .take(10000)

    val incomeIntervals = nationalLarsByIncomeInterval(reportLars)

    val reportDate = formattedCurrentDate

    for {
      ri1 <- raceDispositions(incomeIntervals(LessThan50PercentOfMSAMedian))
      ei1 <- ethnicityDispositions(incomeIntervals(LessThan50PercentOfMSAMedian))
      mi1 <- minorityStatusDispositions(incomeIntervals(LessThan50PercentOfMSAMedian))

      ri2 <- raceDispositions(incomeIntervals(Between50And79PercentOfMSAMedian))
      ei2 <- ethnicityDispositions(incomeIntervals(Between50And79PercentOfMSAMedian))
      mi2 <- minorityStatusDispositions(incomeIntervals(Between50And79PercentOfMSAMedian))

      ri3 <- raceDispositions(incomeIntervals(Between80And99PercentOfMSAMedian))
      ei3 <- ethnicityDispositions(incomeIntervals(Between80And99PercentOfMSAMedian))
      mi3 <- minorityStatusDispositions(incomeIntervals(Between80And99PercentOfMSAMedian))

      ri4 <- raceDispositions(incomeIntervals(Between100And119PercentOfMSAMedian))
      ei4 <- ethnicityDispositions(incomeIntervals(Between100And119PercentOfMSAMedian))
      mi4 <- minorityStatusDispositions(incomeIntervals(Between100And119PercentOfMSAMedian))

      ri5 <- raceDispositions(incomeIntervals(GreaterThan120PercentOfMSAMedian))
      ei5 <- ethnicityDispositions(incomeIntervals(GreaterThan120PercentOfMSAMedian))
      mi5 <- minorityStatusDispositions(incomeIntervals(GreaterThan120PercentOfMSAMedian))

      total <- dispositionsOutput(reportLars)
    } yield {

      val report =
        s"""
           |{
           |    "table": "${metaData.reportTable}",
           |    "type": "${metaData.reportType}",
           |    "description": "${metaData.description}",
           |    "year": "2017",
           |    "reportDate": "$reportDate",
           |    "applicantIncomes": [
           |        {
           |            "applicantIncome": "Less than 50% of MSA/MD median",
           |            "borrowerCharacteristics": [
           |                {
           |                    "characteristic": "Race",
           |                    "races": $ri1
           |                },
           |                {
           |                    "characteristic": "Ethnicity",
           |                    "ethnicities": $ei1
           |                },
           |                {
           |                    "characteristic": "Minority Status",
           |                    "minorityStatus": $mi1
           |                }
           |            ]
           |        },
           |        {
           |            "applicantIncome": "50-79% of MSA/MD median",
           |            "borrowerCharacteristics": [
           |                {
           |                    "characteristic": "Race",
           |                    "races": $ri2
           |                },
           |                {
           |                    "characteristic": "Ethnicity",
           |                    "ethnicities": $ei2
           |                },
           |                {
           |                    "characteristic": "Minority Status",
           |                    "minorityStatus": $mi2
           |                }
           |            ]
           |        },
           |        {
           |            "applicantIncome": "80-99% of MSA/MD median",
           |            "borrowerCharacteristics": [
           |                {
           |                    "characteristic": "Race",
           |                    "races": $ri3
           |                },
           |                {
           |                    "characteristic": "Ethnicity",
           |                    "ethnicities": $ei3
           |                },
           |                {
           |                    "characteristic": "Minority Status",
           |                    "minorityStatus": $mi3
           |                }
           |            ]
           |        },
           |        {
           |            "applicantIncome": "100-119% of MSA/MD median",
           |            "borrowerCharacteristics": [
           |                {
           |                    "characteristic": "Race",
           |                    "races": $ri4
           |                },
           |                {
           |                    "characteristic": "Ethnicity",
           |                    "ethnicities": $ei4
           |                },
           |                {
           |                    "characteristic": "Minority Status",
           |                    "minorityStatus": $mi4
           |                }
           |            ]
           |        },
           |        {
           |            "applicantIncome": "120% or more of MSA/MD median",
           |            "borrowerCharacteristics": [
           |                {
           |                    "characteristic": "Race",
           |                    "races": $ri5
           |                },
           |                {
           |                    "characteristic": "Ethnicity",
           |                    "ethnicities": $ei5
           |                },
           |                {
           |                    "characteristic": "Minority Status",
           |                    "minorityStatus": $mi5
           |                }
           |            ]
           |        }
           |    ],
           |    "total": $total
           |}
         """.stripMargin

      val fipsString = if (metaData.reportType == Aggregate) fipsCode.toString else "nationwide"

      AggregateReportPayload(reportId, fipsString, report)
    }

  }

  private def raceDispositions[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val races = List(AmericanIndianOrAlaskaNative, Asian, BlackOrAfricanAmerican,
      HawaiianOrPacific, White, TwoOrMoreMinority, JointRace, NotProvided)

    val raceOutputs: Future[List[String]] = Future.sequence(
      races.map { race =>
        dispositionsOutput(filterRace(larSource, race)).map { disp =>
          s"""
             |{
             |    "race": "${race.description}",
             |    "dispositions": $disp
             |}
          """.stripMargin
        }
      }
    )

    raceOutputs.map { list => list.mkString("[", ",", "]") }
  }
  private def ethnicityDispositions[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val ethnicities = List(HispanicOrLatino, NotHispanicOrLatino, JointEthnicity, NotAvailable)

    val ethnicityOutputs: Future[List[String]] = Future.sequence(
      ethnicities.map { ethnicity =>
        dispositionsOutput(filterEthnicity(larSource, ethnicity)).map { disp =>
          s"""
             |{
             |    "ethnicity": "${ethnicity.description}",
             |    "dispositions": $disp
             |}
          """.stripMargin
        }
      }
    )

    ethnicityOutputs.map { list => list.mkString("[", ",", "]") }
  }
  private def minorityStatusDispositions[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val minorityStatuses = List(WhiteNonHispanic, OtherIncludingHispanic)

    val minorityStatusOutputs: Future[List[String]] = Future.sequence(
      minorityStatuses.map { minorityStatus =>
        dispositionsOutput(filterMinorityStatus(larSource, minorityStatus)).map { disp =>
          s"""
             |{
             |    "minorityStatus": "${minorityStatus.description}",
             |    "dispositions": $disp
             |}
          """.stripMargin
        }
      }
    )

    minorityStatusOutputs.map { list => list.mkString("[", ",", "]") }
  }

  private def dispositionsOutput[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val calculatedDispositions: Future[List[ValueDisposition]] = Future.sequence(
      dispositions.map(_.calculateValueDisposition(larSource))
    )

    calculatedDispositions.map(list => list.map(_.toJsonFormat).mkString("[", ",", "]"))
  }
}
