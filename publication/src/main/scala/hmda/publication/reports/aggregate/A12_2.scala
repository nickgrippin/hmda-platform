package hmda.publication.reports.aggregate

import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ApplicantIncomeEnum._
import hmda.model.publication.reports.EthnicityEnum._
import hmda.model.publication.reports.GenderEnum._
import hmda.model.publication.reports.MinorityStatusEnum._
import hmda.model.publication.reports.RaceEnum._
import hmda.model.publication.reports.ReportTypeEnum.Aggregate
import hmda.publication.model.LARTable
import hmda.publication.reports._
import hmda.publication.reports.util.db.CensusTractUtilDB._
import hmda.publication.reports.util.db.EthnicityUtilDB.filterEthnicity
import hmda.publication.reports.util.db.GenderUtilDB.filterGender
import hmda.publication.reports.util.db.MinorityStatusUtilDB.filterMinorityStatus
import hmda.publication.reports.util.db.PricingDataUtilDB.pricingData
import hmda.publication.reports.util.db.RaceUtilDB.filterRace
import hmda.publication.reports.util.db.ReportUtilDB._
import hmda.publication.reports.util.ReportsMetaDataLookup

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

/*
object A12_2 extends A12_2X {
  val reportId = "A12-2"
  def filters(lar: LoanApplicationRegister): Boolean = {
    lar.loan.loanType == 1 &&
      lar.loan.purpose == 1 &&
      lar.lienStatus == 1 &&
      lar.loan.propertyType == 2 &&
      lar.loan.occupancy == 1
  }
}*/

object N12_2 extends A12_2X {
  val reportId = "N12-2"
  def filters(lars: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq] = {
    lars.filter(lar => {
      lar.loanType === 1 &&
        lar.loanPurpose === 1 &&
        lar.lienStatus === 1 &&
        lar.loanPropertyType === 2 &&
        lar.loanOccupancy === 1
    })
  }
}

trait A12_2X {
  val reportId: String
  def filters(lar: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq]

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
      e1 <- pricingData(filterEthnicity(lars, HispanicOrLatino))
      e2 <- pricingData(filterEthnicity(lars, NotHispanicOrLatino))
      e3 <- pricingData(filterEthnicity(lars, JointEthnicity))
      e4 <- pricingData(filterEthnicity(lars, NotAvailable))

      r1 <- pricingData(filterRace(lars, AmericanIndianOrAlaskaNative))
      r2 <- pricingData(filterRace(lars, Asian))
      r3 <- pricingData(filterRace(lars, BlackOrAfricanAmerican))
      r4 <- pricingData(filterRace(lars, HawaiianOrPacific))
      r5 <- pricingData(filterRace(lars, White))
      r6 <- pricingData(filterRace(lars, TwoOrMoreMinority))
      r7 <- pricingData(filterRace(lars, JointRace))
      r8 <- pricingData(filterRace(lars, NotProvided))

      m1 <- pricingData(filterMinorityStatus(lars, WhiteNonHispanic))
      m2 <- pricingData(filterMinorityStatus(lars, OtherIncludingHispanic))

      g1 <- pricingData(filterGender(lars, Male))
      g2 <- pricingData(filterGender(lars, Female))
      g3 <- pricingData(filterGender(lars, JointGender))
      g4 <- pricingData(filterGender(lars, GenderNotAvailable))

      i1 <- pricingData(incomeIntervals(LessThan50PercentOfMSAMedian))
      i2 <- pricingData(incomeIntervals(Between50And79PercentOfMSAMedian))
      i3 <- pricingData(incomeIntervals(Between80And99PercentOfMSAMedian))
      i4 <- pricingData(incomeIntervals(Between100And119PercentOfMSAMedian))
      i5 <- pricingData(incomeIntervals(GreaterThan120PercentOfMSAMedian))
      i6 <- pricingData(lars.filter(lar => lar.applicantIncome === "NA"))

      tractMinorityComposition1 <- pricingData(filterMinorityPopulation(lars, 0, 10))
      tractMinorityComposition2 <- pricingData(filterMinorityPopulation(lars, 10, 20))
      tractMinorityComposition3 <- pricingData(filterMinorityPopulation(lars, 20, 50))
      tractMinorityComposition4 <- pricingData(filterMinorityPopulation(lars, 50, 80))
      tractMinorityComposition5 <- pricingData(filterMinorityPopulation(lars, 80, 101))

      tractIncome1 <- pricingData(filterIncomeCharacteristics(lars, 0, 50))
      tractIncome2 <- pricingData(filterIncomeCharacteristics(lars, 50, 80))
      tractIncome3 <- pricingData(filterIncomeCharacteristics(lars, 80, 120))
      tractIncome4 <- pricingData(filterIncomeCharacteristics(lars, 120, 1000))

    } yield {
      val report = s"""
       |{
       |    "table": "${metaData.reportTable}",
       |    "type": "Disclosure",
       |    "description": "${metaData.description}",
       |    "year": "2017",
       |    "reportDate": "$reportDate",
       |    "borrowerCharacteristics": [
       |        {
       |            "characteristic": "Race",
       |            "races": [
       |                {
       |                    "race": "American Indian/Alaska Native",
       |                    "pricingInformation": $r1
       |                },
       |                {
       |                    "race": "Asian",
       |                    "pricingInformation": $r2
       |                },
       |                {
       |                    "race": "Black or African American",
       |                    "pricingInformation": $r3
       |                },
       |                {
       |                    "race": "Native Hawaiian or Other Pacific Islander",
       |                    "pricingInformation": $r4
       |                },
       |                {
       |                    "race": "White",
       |                    "pricingInformation": $r5
       |                },
       |                {
       |                    "race": "2 or more minority races",
       |                    "pricingInformation": $r6
       |                },
       |                {
       |                    "race": "Joint (White/Minority Race)",
       |                    "pricingInformation": $r7
       |                },
       |                {
       |                    "race": "Race Not Available",
       |                    "pricingInformation": $r8
       |                }
       |            ]
       |        },
       |        {
       |            "characteristic": "Ethnicity",
       |            "ethnicities": [
       |                {
       |                    "ethnicity": "Hispanic or Latino",
       |                    "pricingInformation": $e1
       |                },
       |                {
       |                    "ethnicity": "Not Hispanic or Latino",
       |                    "pricingInformation": $e2
       |                },
       |                {
       |                    "ethnicity": "Joint (Hispanic or Latino/Not Hispanic or Latino)",
       |                    "pricingInformation": $e3
       |                },
       |                {
       |                    "ethnicity": "Ethnicity Not Available",
       |                    "pricingInformation": $e4
       |                }
       |            ]
       |        },
       |        {
       |            "characteristic": "Minority Status",
       |            "minorityStatuses": [
       |                {
       |                    "minorityStatus": "White Non-Hispanic",
       |                    "pricingInformation": $m1
       |                },
       |                {
       |                    "minorityStatus": "Others, Including Hispanic",
       |                    "pricingInformation": $m2
       |                }
       |            ]
       |        },
       |        {
       |            "characteristic": "Income",
       |            "incomes": [
       |                {
       |                    "income": "Less than 50% of MSA/MD median",
       |                    "pricingInformation": $i1
       |                },
       |                {
       |                    "income": "50-79% of MSA/MD median",
       |                    "pricingInformation": $i2
       |                },
       |                {
       |                    "income": "80-99% of MSA/MD median",
       |                    "pricingInformation": $i3
       |                },
       |                {
       |                    "income": "100-119% of MSA/MD median",
       |                    "pricingInformation": $i4
       |                },
       |                {
       |                    "income": "120% or more of MSA/MD median",
       |                    "pricingInformation": $i5
       |                },
       |                {
       |                    "income": "Income Not Available",
       |                    "pricingInformation": $i6
       |                }
       |            ]
       |        },
       |        {
       |            "characteristic": "Gender",
       |            "genders": [
       |                {
       |                    "gender": "Male",
       |                    "pricingInformation": $g1
       |                },
       |                {
       |                    "gender": "Female",
       |                    "pricingInformation": $g2
       |                },
       |                {
       |                    "gender": "Joint (Male/Female)",
       |                    "pricingInformation": $g3
       |                },
       |                {
       |                    "gender": "Gender Not Available",
       |                    "pricingInformation": $g4
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
       |                    "pricingInformation": $tractMinorityComposition1
       |                },
       |                {
       |                    "composition": "10-19% minority",
       |                    "pricingInformation": $tractMinorityComposition2
       |                },
       |                {
       |                    "composition": "20-49% minority",
       |                    "pricingInformation": $tractMinorityComposition3
       |                },
       |                {
       |                    "composition": "50-79% minority",
       |                    "pricingInformation": $tractMinorityComposition4
       |                },
       |                {
       |                    "composition": "80-100% minority",
       |                    "pricingInformation": $tractMinorityComposition5
       |                }
       |            ]
       |        },
       |        {
       |            "characteristic": "Income Characteristics",
       |            "incomes": [
       |                {
       |                    "income": "Low income",
       |                    "pricingInformation": $tractIncome1
       |                },
       |                {
       |                    "income": "Moderate income",
       |                    "pricingInformation": $tractIncome2
       |                },
       |                {
       |                    "income": "Middle income",
       |                    "pricingInformation": $tractIncome3
       |                },
       |                {
       |                    "income": "Upper income",
       |                    "pricingInformation": $tractIncome4
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

}
