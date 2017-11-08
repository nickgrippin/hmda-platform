package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ApplicantIncomeEnum._
import hmda.model.publication.reports.EthnicityEnum._
import hmda.model.publication.reports.{ ApplicantIncome, Disposition, MSAReport }
import hmda.model.publication.reports.MinorityStatusEnum._
import hmda.model.publication.reports.RaceEnum._
import hmda.publication.reports._
import hmda.publication.reports.util.RaceUtil.filterRace
import hmda.publication.reports.util.EthnicityUtil.filterEthnicity
import hmda.publication.reports.util.MinorityStatusUtil.filterMinorityStatus
import hmda.publication.reports.util.DispositionType._
import hmda.publication.reports.util.ReportUtil._

import scala.concurrent.Future
import spray.json._

case class A31(
  year: Int,
  msa: MSAReport,
  applicantIncomes: List[ApplicantIncome],
  total: List[Disposition],
  table: String,
  description: String,
  reportDate: String = formattedCurrentDate
) extends AggregateReport

object A31 {
  def generate[ec: EC, mat: MAT, as: AS](
    larSource: Source[LoanApplicationRegister, NotUsed],
    fipsCode: Int
  ): Future[JsValue] = {
    val incomeIntervals = larsByIncomeInterval(larSource.filter(lar => lar.applicant.income != "NA"), calculateMedianIncomeIntervals(fipsCode))
    val msa = msaReport(fipsCode.toString).toJsonFormat
    val date = formattedCurrentDate
    for {
      r1 <- getDispositionsString(filterRace(larSource, AmericanIndianOrAlaskaNative))
      r2 <- getDispositionsString(filterRace(larSource, Asian))
      r3 <- getDispositionsString(filterRace(larSource, BlackOrAfricanAmerican))
      r4 <- getDispositionsString(filterRace(larSource, HawaiianOrPacific))
      r5 <- getDispositionsString(filterRace(larSource, White))
      r6 <- getDispositionsString(filterRace(larSource, TwoOrMoreMinority))
      r7 <- getDispositionsString(filterRace(larSource, JointRace))
      r8 <- getDispositionsString(filterRace(larSource, NotProvided))
      e1 <- getDispositionsString(filterEthnicity(larSource, HispanicOrLatino))
      e2 <- getDispositionsString(filterEthnicity(larSource, NotHispanicOrLatino))
      e3 <- getDispositionsString(filterEthnicity(larSource, JointEthnicity))
      e4 <- getDispositionsString(filterEthnicity(larSource, NotAvailable))
      m1 <- getDispositionsString(filterMinorityStatus(larSource, WhiteNonHispanic))
      m2 <- getDispositionsString(filterMinorityStatus(larSource, OtherIncludingHispanic))
      i1 <- getDispositionsString(incomeIntervals.get(LessThan50PercentOfMSAMedian).get)
      i2 <- getDispositionsString(incomeIntervals.get(Between50And79PercentOfMSAMedian).get)
      i3 <- getDispositionsString(incomeIntervals.get(Between80And99PercentOfMSAMedian).get)
      i4 <- getDispositionsString(incomeIntervals.get(Between100And119PercentOfMSAMedian).get)
      i5 <- getDispositionsString(incomeIntervals.get(GreaterThan120PercentOfMSAMedian).get)
      i6 <- getDispositionsString(larSource.filter(lar => lar.applicant.income == "NA"))
      t <- getDispositionsString(larSource)
    } yield {
      s"""
         |{
         |  "table": "3-1",
         |  "type": "Aggregate",
         |  "desc": "Loans sold, by characteristics of borrower and census tract in which property is located and by type of purchaser (includes originations and purchased loans)",
         |  "year": "2017",
         |  "report-date": "$date",
         |  "msa": $msa,
         |  "borrowercharacteristics": [
         |  {
         |    "characteristic": "Race",
         |    "races": [
         |    {
         |      "race": "American Indian/Alaska Native",
         |      "purchasers": $r1
         |    },
         |    {
         |      "race": "Asian",
         |      "purchasers": $r2
         |    },
         |    {
         |      "race": "Black or African American",
         |      "purchasers": $r3
         |    },
         |    {
         |      "race": "Native Hawaiian or Other Pacific Islander",
         |      "purchasers": $r4
         |    },
         |    {
         |      "race": "White",
         |      "purchasers": $r5
         |    },
         |    {
         |      "race": "2 or more minority races",
         |      "purchasers": $r6
         |    },
         |    {
         |      "race": "Joint (White/Minority Race)",
         |      "purchasers": $r7
         |    },
         |    {
         |      "race": "Race Not Available",
         |      "purchasers": $r8
         |    }
         |    ]
         |  },
         |  {
         |    "characteristic": "Ethnicity",
         |    "ethnicities": [
         |    {
         |      "ethnicity": "Hispanic or Latino",
         |      "purchasers": $e1
         |    },
         |    {
         |      "ethnicity": "Not Hispanic or Latino",
         |      "purchasers": $e2
         |    },
         |    {
         |      "ethnicity": "Joint (Hispanic or Latino/Not Hispanic or Latino)",
         |      "purchasers": $e3
         |    },
         |    {
         |      "ethnicity": "Ethnicity Not Available",
         |      "purchasers": $e4
         |    }
         |    ]
         |  },
         |  {
         |    "characteristic": "Minority Status",
         |    "minoritystatuses": [
         |    {
         |      "minoritystatus": "White Non-Hispanic",
         |      "purchasers": $m1
         |    },
         |    {
         |      "minoritystatus": "Others, Including Hispanic",
         |      "purchasers": $m2
         |    }
         |    ]
         |  },
         |  {
         |    "characteristic": "Applicant Income",
         |    "applicantincomes": [
         |    {
         |      "applicantincome": "Less than 50% of MSA/MD median",
         |      "purchasers": $i1
         |    },
         |    {
         |      "applicantincome": "50-79% of MSA/MD median",
         |      "purchasers": $i2
         |    },
         |    {
         |      "applicantincome": "80-99% of MSA/MD median",
         |      "purchasers": $i3
         |    },
         |    {
         |      "applicantincome": "100-119% of MSA/MD median",
         |      "purchasers": $i4
         |    },
         |    {
         |      "applicantincome": "120% or more of MSA/MD median",
         |      "purchasers": $i5
         |    },
         |    {
         |      "applicantincome": "Income Not Available",
         |      "purchasers": $i6
         |    }
         |    ]
         |  }
         |  ],
         |  "censuscharacteristics": [
         |  {
         |    "characteristic": "Racial/Ethnic Composition",
         |    "tractpctminorities": [
         |    {
         |      "tractpctminority": "Less than 10% minority",
         |      "purchasers": {}
         |    },
         |    {
         |      "tractpctminority": "10-19% minority",
         |      "purchasers": {}
         |    },
         |    {
         |      "tractpctminority": "20-49% minority",
         |      "purchasers": {}
         |    },
         |    {
         |      "tractpctminority": "50-79% minority",
         |      "purchasers": {}
         |    },
         |    {
         |      "tractpctminority": "80-100% minority",
         |      "purchasers": {}
         |    }
         |    ]
         |  },
         |  {
         |    "characteristic": "Income",
         |    "incomelevels": [
         |    {
         |      "incomelevel": "Low income",
         |      "purchasers": {}
         |    },
         |    {
         |      "incomelevel": "Moderate income",
         |      "purchasers": {}
         |    },
         |    {
         |      "incomelevel": "Middle income",
         |      "purchasers": {}
         |    },
         |    {
         |      "incomelevel": "Upper income",
         |      "purchasers": {}
         |    }
         |    ]
         |  }
         |  ],
         |  "total": {
         |    "purchasers": $t
         |  }
         |}
       """.stripMargin.parseJson
    }
  }

  // Calculate the column values for a pre-filtered row
  def getDispositionsString[ec: EC, mat: MAT, as: AS](larSource: Source[LoanApplicationRegister, NotUsed]): Future[String] = {
    for {
      fMae <- FannieMaeDisp.calculateDisposition(larSource)
      gMae <- GinnieMaeDisp.calculateDisposition(larSource)
      frMac <- FreddieMacDisp.calculateDisposition(larSource)
      faMac <- FarmerMacDisp.calculateDisposition(larSource)
      pSec <- PrivateSecuritizationDisp.calculateDisposition(larSource)
      cBnk <- CommercialBankDisp.calculateDisposition(larSource)
      fCmp <- FinanceCompanyDisp.calculateDisposition(larSource)
      aff <- AffiliateDisp.calculateDisposition(larSource)
      other <- OtherPurchaserDisp.calculateDisposition(larSource)
    } yield {
      s"""[
      |  ${fMae.toJsonFormat},
      |  ${gMae.toJsonFormat},
      |  ${frMac.toJsonFormat},
      |  ${faMac.toJsonFormat},
      |  ${pSec.toJsonFormat},
      |  ${cBnk.toJsonFormat},
      |  ${fCmp.toJsonFormat},
      |  ${aff.toJsonFormat},
      |  ${other.toJsonFormat}
      ]""".stripMargin
    }
  }
}
