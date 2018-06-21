package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.census.model.{ Tract, TractLookup }
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.EthnicityEnum._
import hmda.model.publication.reports.GenderEnum.{ Female, GenderNotAvailable, JointGender, Male }
import hmda.model.publication.reports.ApplicantIncomeEnum._
import hmda.model.publication.reports.MinorityStatusEnum._
import hmda.model.publication.reports.RaceEnum._
import hmda.model.publication.reports.ReportTypeEnum.Aggregate
import hmda.model.publication.reports.ValueDisposition
import hmda.publication.model.LARTable
import hmda.publication.reports.util.db.CensusTractUtilDB._
import hmda.publication.reports.util.db.DispositionTypeDB._
import hmda.publication.reports.EC
import hmda.publication.reports.util.db.EthnicityUtilDB.filterEthnicity
import hmda.publication.reports.util.db.GenderUtilDB.filterGender
import hmda.publication.reports.util.db.MinorityStatusUtilDB.filterMinorityStatus
import hmda.publication.reports.util.db.RaceUtilDB.filterRace
import hmda.publication.reports.util.db.ReportUtilDB._
import hmda.publication.reports.util.ReportsMetaDataLookup

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

/*
object A12_1 extends A12_1X {
  override val reportId = "A12-1"
}*/

object N12_1 extends A12_1X {
  override val reportId = "N12-1"
}

trait A12_1X {
  val reportId: String
  def filters(lars: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq] = {
    lars.filter(lar => {
      lar.loanType === 1 && lar.loanPurpose === 1 && lar.lienStatus === 1 &&
        lar.loanPropertyType === 2 && lar.loanOccupancy === 1
    })
  }

  val dispositions = List(ApplicationReceived, LoansOriginated, ApprovedButNotAccepted,
    ApplicationsDenied, ApplicationsWithdrawn, ClosedForIncompleteness)

  def geoFilter(fips: Int)(lar: LoanApplicationRegister): Boolean =
    lar.geography.msa != "NA" &&
      lar.geography.msa.toInt == fips

  def generate[ec: EC](
    larSource: TableQuery[LARTable],
    fipsCode: Int
  ): Future[AggregateReportPayload] = {

    val metaData = ReportsMetaDataLookup.values(reportId)

    val lars = filters(larSource)

    val larsForIncomeCalculation = lars.filter(lar => lar.applicantIncome =!= "NA" && lar.msaMedIncome > 0)
    val incomeIntervals = nationalLarsByIncomeInterval(larsForIncomeCalculation)

    val reportDate = formattedCurrentDate

    for {
      e1 <- dispositionsOutput(filterEthnicity(lars, HispanicOrLatino))
      e2 <- dispositionsOutput(filterEthnicity(lars, NotHispanicOrLatino))
      e3 <- dispositionsOutput(filterEthnicity(lars, JointEthnicity))
      e4 <- dispositionsOutput(filterEthnicity(lars, NotAvailable))

      r1 <- dispositionsOutput(filterRace(lars, AmericanIndianOrAlaskaNative))
      r2 <- dispositionsOutput(filterRace(lars, Asian))
      r3 <- dispositionsOutput(filterRace(lars, BlackOrAfricanAmerican))
      r4 <- dispositionsOutput(filterRace(lars, HawaiianOrPacific))
      r5 <- dispositionsOutput(filterRace(lars, White))
      r6 <- dispositionsOutput(filterRace(lars, TwoOrMoreMinority))
      r7 <- dispositionsOutput(filterRace(lars, JointRace))
      r8 <- dispositionsOutput(filterRace(lars, NotProvided))

      m1 <- dispositionsOutput(filterMinorityStatus(lars, WhiteNonHispanic))
      m2 <- dispositionsOutput(filterMinorityStatus(lars, OtherIncludingHispanic))

      g1 <- dispositionsOutput(filterGender(lars, Male))
      g2 <- dispositionsOutput(filterGender(lars, Female))
      g3 <- dispositionsOutput(filterGender(lars, JointGender))
      g4 <- dispositionsOutput(filterGender(lars, GenderNotAvailable))

      i1 <- dispositionsOutput(incomeIntervals(LessThan50PercentOfMSAMedian))
      i2 <- dispositionsOutput(incomeIntervals(Between50And79PercentOfMSAMedian))
      i3 <- dispositionsOutput(incomeIntervals(Between80And99PercentOfMSAMedian))
      i4 <- dispositionsOutput(incomeIntervals(Between100And119PercentOfMSAMedian))
      i5 <- dispositionsOutput(incomeIntervals(GreaterThan120PercentOfMSAMedian))
      i6 <- dispositionsOutput(lars.filter(lar => lar.applicantIncome === "NA"))

      tractMinorityComposition1 <- dispositionsOutput(filterMinorityPopulation(lars, 0, 10))
      tractMinorityComposition2 <- dispositionsOutput(filterMinorityPopulation(lars, 10, 20))
      tractMinorityComposition3 <- dispositionsOutput(filterMinorityPopulation(lars, 20, 50))
      tractMinorityComposition4 <- dispositionsOutput(filterMinorityPopulation(lars, 50, 80))
      tractMinorityComposition5 <- dispositionsOutput(filterMinorityPopulation(lars, 80, 101))

      tractIncome1 <- dispositionsOutput(filterIncomeCharacteristics(lars, 0, 50))
      tractIncome2 <- dispositionsOutput(filterIncomeCharacteristics(lars, 50, 80))
      tractIncome3 <- dispositionsOutput(filterIncomeCharacteristics(lars, 80, 120))
      tractIncome4 <- dispositionsOutput(filterIncomeCharacteristics(lars, 120, 1000))

    } yield {
      val report = s"""
                      |{
                      |    "table": "${metaData.reportTable}",
                      |    "type": "${metaData.reportType}",
                      |    "description": "${metaData.description}",
                      |    "year": "2017",
                      |    "reportDate": "$reportDate",
                      |    "borrowerCharacteristics": [
                      |        {
                      |            "characteristic": "Race",
                      |            "races": [
                      |                {
                      |                    "race": "American Indian/Alaska Native",
                      |                    "dispositions": $r1
                      |                },
                      |                {
                      |                    "race": "Asian",
                      |                    "dispositions": $r2
                      |                },
                      |                {
                      |                    "race": "Black or African American",
                      |                    "dispositions": $r3
                      |                },
                      |                {
                      |                    "race": "Native Hawaiian or Other Pacific Islander",
                      |                    "dispositions": $r4
                      |                },
                      |                {
                      |                    "race": "White",
                      |                    "dispositions": $r5
                      |                },
                      |                {
                      |                    "race": "2 or more minority races",
                      |                    "dispositions": $r6
                      |                },
                      |                {
                      |                    "race": "Joint (White/Minority Race)",
                      |                    "dispositions": $r7
                      |                },
                      |                {
                      |                    "race": "Race Not Available",
                      |                    "dispositions": $r8
                      |                }
                      |            ]
                      |        },
                      |        {
                      |            "characteristic": "Ethnicity",
                      |            "ethnicities": [
                      |                {
                      |                    "ethnicity": "Hispanic or Latino",
                      |                    "dispositions": $e1
                      |                },
                      |                {
                      |                    "ethnicity": "Not Hispanic or Latino",
                      |                    "dispositions": $e2
                      |                },
                      |                {
                      |                    "ethnicity": "Joint (Hispanic or Latino/Not Hispanic or Latino)",
                      |                    "dispositions": $e3
                      |                },
                      |                {
                      |                    "ethnicity": "Ethnicity Not Available",
                      |                    "dispositions": $e4
                      |                }
                      |            ]
                      |        },
                      |        {
                      |            "characteristic": "Minority Status",
                      |            "minorityStatuses": [
                      |                {
                      |                    "minorityStatus": "White Non-Hispanic",
                      |                    "dispositions": $m1
                      |                },
                      |                {
                      |                    "minorityStatus": "Others, Including Hispanic",
                      |                    "dispositions": $m2
                      |                }
                      |            ]
                      |        },
                      |        {
                      |            "characteristic": "Income",
                      |            "incomes": [
                      |                {
                      |                    "income": "Less than 50% of MSA/MD median",
                      |                    "dispositions": $i1
                      |                },
                      |                {
                      |                    "income": "50-79% of MSA/MD median",
                      |                    "dispositions": $i2
                      |                },
                      |                {
                      |                    "income": "80-99% of MSA/MD median",
                      |                    "dispositions": $i3
                      |                },
                      |                {
                      |                    "income": "100-119% of MSA/MD median",
                      |                    "dispositions": $i4
                      |                },
                      |                {
                      |                    "income": "120% or more of MSA/MD median",
                      |                    "dispositions": $i5
                      |                },
                      |                {
                      |                    "income": "Income Not Available",
                      |                    "dispositions": $i6
                      |                }
                      |            ]
                      |        },
                      |        {
                      |            "characteristic": "Gender",
                      |            "genders": [
                      |                {
                      |                    "gender": "Male",
                      |                    "dispositions": $g1
                      |                },
                      |                {
                      |                    "gender": "Female",
                      |                    "dispositions": $g2
                      |                },
                      |                {
                      |                    "gender": "Joint (Male/Female)",
                      |                    "dispositions": $g3
                      |                },
                      |                {
                      |                    "gender": "Gender Not Available",
                      |                    "dispositions": $g4
                      |                }
                      |            ]
                      |        }
                      |    ],
                      |    "censusTractCharacteristics": [
                      |        {
                      |            "characteristic": "Racial/Ethnic Composition",
                      |            "compositions": [
                      |                {
                      |                    "composition": "Less than 10% minority",
                      |                    "dispositions": $tractMinorityComposition1
                      |                },
                      |                {
                      |                    "composition": "10-19% minority",
                      |                    "dispositions": $tractMinorityComposition2
                      |                },
                      |                {
                      |                    "composition": "20-49% minority",
                      |                    "dispositions": $tractMinorityComposition3
                      |                },
                      |                {
                      |                    "composition": "50-79% minority",
                      |                    "dispositions": $tractMinorityComposition4
                      |                },
                      |                {
                      |                    "composition": "80-100% minority",
                      |                    "dispositions": $tractMinorityComposition5
                      |                }
                      |            ]
                      |        },
                      |        {
                      |            "characteristic": "Income Characteristics",
                      |            "incomes": [
                      |                {
                      |                    "income": "Low income",
                      |                    "dispositions": $tractIncome1
                      |                },
                      |                {
                      |                    "income": "Moderate income",
                      |                    "dispositions": $tractIncome2
                      |                },
                      |                {
                      |                    "income": "Middle income",
                      |                    "dispositions": $tractIncome3
                      |                },
                      |                {
                      |                    "income": "Upper income",
                      |                    "dispositions": $tractIncome4
                      |                }
                      |            ]
                      |        }
                      |    ]
                      |}
     """.stripMargin

      val fipsString = if (metaData.reportType == Aggregate) fipsCode.toString else "nationwide"

      AggregateReportPayload(metaData.reportTable, fipsString, report)
    }
  }

  private def dispositionsOutput[ec: EC](larSource: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    val calculatedDispositions: Future[List[ValueDisposition]] = Future.sequence(
      dispositions.map(_.calculateValueDisposition(larSource))
    )

    calculatedDispositions.map { list =>
      list.map(disp => disp.toJsonFormat).mkString("[", ",", "]")
    }
  }
}
