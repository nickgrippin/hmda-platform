package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.RaceEnum._
import hmda.publication.reports._
import hmda.publication.reports.util.RaceUtil.filterRace
import hmda.publication.reports.util.DispositionType._

import scala.concurrent.Future

object A31 {
  def generate[ec: EC, mat: MAT, as: AS](
    larSource: Source[LoanApplicationRegister, NotUsed],
    fipsCode: Int
  ): Future[A31] = {
    val report =
      for {
        r1 <- getDispositions(filterRace(larSource, AmericanIndianOrAlaskaNative))
        r2 <- getDispositions(filterRace(larSource, Asian))
        r3 <- getDispositions(filterRace(larSource, BlackOrAfricanAmerican))
        r4 <- getDispositions(filterRace(larSource, HawaiianOrPacific))
        r5 <- getDispositions(filterRace(larSource, White))
        r6 <- getDispositions(filterRace(larSource, TwoOrMoreMinority))
        r7 <- getDispositions(filterRace(larSource, Joint))
        r8 <- getDispositions(filterRace(larSource, NotProvided))
      }
        """{
    "table": "3-1",
    "type": "Aggregate",
    "desc": "Loans sold, by characteristics of borrower and census tract in which property is located and by type of purchaser (includes originations and purchased loans)",
    "year": "2017",
    "report-date": "",
    "msa": {},
    "borrowercharacteristics": [
    {
      "characteristic": "Race",
      "races": [
      {
        "race": "American Indian/Alaska Native",
        "purchasers": {}
      },
      {
        "race": "Asian",
        "purchasers": {}
      },
      {
        "race": "Black or African American",
        "purchasers": {}
      },
      {
        "race": "Native Hawaiian or Other Pacific Islander",
        "purchasers": {}
      },
      {
        "race": "White",
        "purchasers": {}
      },
      {
        "race": "2 or more minority races",
        "purchasers": {}
      },
      {
        "race": "Joint (White/Minority Race)",
        "purchasers": {}
      },
      {
        "race": "Race Not Available",
        "purchasers": {}
      }
      ]
    },
    {
      "characteristic": "Ethnicity",
      "ethnicities": [
      {
        "ethnicity": "Hispanic or Latino",
        "purchasers": {}
      },
      {
        "ethnicity": "Not Hispanic or Latino",
        "purchasers": {}
      },
      {
        "ethnicity": "Joint (Hispanic or Latino/Not Hispanic or Latino)",
        "purchasers": {}
      },
      {
        "ethnicity": "Ethnicity Not Available",
        "purchasers": {}
      }
      ]
    },
    {
      "characteristic": "Minority Status",
      "minoritystatuses": [
      {
        "minoritystatus": "White Non-Hispanic",
        "purchasers": {}
      },
      {
        "minoritystatus": "Others, Including Hispanic",
        "purchasers": {}
      }
      ]
    },
    {
      "characteristic": "Applicant Income",
      "applicantincomes": [
      {
        "applicantincome": "Less than 50% of MSA/MD median",
        "purchasers": {}
      },
      {
        "applicantincome": "50-79% of MSA/MD median",
        "purchasers": {}
      },
      {
        "applicantincome": "80-99% of MSA/MD median",
        "purchasers": {}
      },
      {
        "applicantincome": "100-119% of MSA/MD median",
        "purchasers": {}
      },
      {
        "applicantincome": "120% or more of MSA/MD median",
        "purchasers": {}
      },
      {
        "applicantincome": "Income Not Available",
        "purchasers": {}
      }
      ]
    }
    ],
    "censuscharacteristics": [
    {
      "characteristic": "Racial/Ethnic Composition",
      "tractpctminorities": [
      {
        "tractpctminority": "Less than 10% minority",
        "purchasers": {}
      },
      {
        "tractpctminority": "10-19% minority",
        "purchasers": {}
      },
      {
        "tractpctminority": "20-49% minority",
        "purchasers": {}
      },
      {
        "tractpctminority": "50-79% minority",
        "purchasers": {}
      },
      {
        "tractpctminority": "80-100% minority",
        "purchasers": {}
      }
      ]
    },
    {
      "characteristic": "Income",
      "incomelevels": [
      {
        "incomelevel": "Low income",
        "purchasers": {}
      },
      {
        "incomelevel": "Moderate income",
        "purchasers": {}
      },
      {
        "incomelevel": "Middle income",
        "purchasers": {}
      },
      {
        "incomelevel": "Upper income",
        "purchasers": {}
      }
      ]
    }
    ],
    "total": {
      "purchasers": {}
    }
  }"""
  }

  def getDispositions(larSource: Source[LoanApplicationRegister, NotUsed]): Unit = {
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
      """[
        {
          "name": "Fannie Mae",
          "count": 9,
          "value": 1206
        },
        {
          "name": "Ginnie Mae",
          "count": 7,
          "value": 562
        },
        {
          "name": "Freddie Mac",
          "count": 4,
          "value": 576
        },
        {
          "name": "Farmer Mac",
          "count": 0,
          "value": 0
        },
        {
          "name": "Private Securitization",
          "count": 0,
          "value": 0
        },
        {
          "name": "Commercial bank, savings bank or association",
          "count": 2,
          "value": 137
        },
        {
          "name": "Life insurance co., credit union, finance co.",
          "count": 3,
          "value": 245
        },
        {
          "name": "Affiliate institution",
          "count": 1,
          "value": 103
        },
        {
          "name": "Other",
          "count": 2,
          "value": 291
        }
      ]"""
    }
  }
}
