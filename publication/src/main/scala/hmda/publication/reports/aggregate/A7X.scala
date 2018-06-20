package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.census.model.{ Tract, TractLookup }
import hmda.model.census.CBSATractLookup
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ValueDisposition
import hmda.model.publication.reports.ReportTypeEnum.Aggregate
import hmda.publication.model.LARTable
import hmda.publication.reports._
import hmda.publication.reports.util.db.CensusTractUtilDB._
import hmda.publication.reports.util.db.DispositionTypeDB._
import hmda.publication.reports.util.db.ReportUtilDB._
import hmda.publication.reports.util.ReportsMetaDataLookup

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
/*
object A71 extends A7X {
  val reportId = "A71"
  def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    (loan.loanType == 2 || loan.loanType == 3 || loan.loanType == 4) &&
      (loan.propertyType == 1 || loan.propertyType == 2) &&
      loan.purpose == 1
  }
}

object A72 extends A7X {
  val reportId = "A72"
  def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    loan.loanType == 1 && loan.purpose == 1 &&
      (loan.propertyType == 1 || loan.propertyType == 2)
  }
}

object A73 extends A7X {
  val reportId = "A73"
  def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    (loan.propertyType == 1 || loan.propertyType == 2) &&
      loan.purpose == 3
  }
}

object A74 extends A7X {
  val reportId = "A74"
  def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    (loan.propertyType == 1 || loan.propertyType == 2) &&
      loan.purpose == 2
  }
}

object A75 extends A7X {
  val reportId = "A75"
  def filters(lar: LoanApplicationRegister): Boolean = {
    lar.loan.propertyType == 3
  }
}

object A76 extends A7X {
  val reportId = "A76"
  def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    loan.occupancy == 2 &&
      (loan.purpose == 1 || loan.purpose == 2 || loan.purpose == 3) &&
      (loan.propertyType == 1 || loan.propertyType == 2)
  }
}

object A77 extends A7X {
  val reportId = "A77"
  def filters(lar: LoanApplicationRegister): Boolean = {
    val loan = lar.loan
    loan.propertyType == 2 &&
      (loan.purpose == 1 || loan.purpose == 2 || loan.purpose == 3)
  }
}*/

object N71 extends A7X {
  val reportId = "N71"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      (lar.loanType === 2 || lar.loanType === 3 || lar.loanType === 4) &&
        (lar.loanPropertyType === 1 || lar.loanPropertyType === 2) &&
        lar.loanPurpose === 1
    })
  }
}

object N72 extends A7X {
  val reportId = "N72"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      lar.loanType === 1 && lar.loanPurpose === 1 &&
        (lar.loanPropertyType === 1 || lar.loanPropertyType === 2)
    })
  }
}

object N73 extends A7X {
  val reportId = "N73"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      (lar.loanPropertyType === 1 || lar.loanPropertyType === 2) &&
        lar.loanPurpose === 3
    })
  }
}

object N74 extends A7X {
  val reportId = "N74"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      (lar.loanPropertyType === 1 || lar.loanPropertyType === 2) &&
        lar.loanPurpose === 2
    })
  }
}

object N75 extends A7X {
  val reportId = "N75"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      lar.loanPropertyType === 3
    })
  }
}

object N76 extends A7X {
  val reportId = "N76"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      lar.loanOccupancy === 2 &&
        (lar.loanPurpose === 1 || lar.loanPurpose === 2 || lar.loanPurpose === 3) &&
        (lar.loanPropertyType === 1 || lar.loanPropertyType === 2)
    })
  }
}

object N77 extends A7X {
  val reportId = "N77"
  def filters(lars: TableQuery[LARTable]) = {
    lars.filter(lar => {
      lar.loanPropertyType === 2 &&
        (lar.loanPurpose === 1 || lar.loanPurpose === 2 || lar.loanPurpose === 3)
    })
  }
}

trait A7X {
  val reportId: String
  def filters(lar: TableQuery[LARTable]): Query[LARTable, LARTable#TableElementType, Seq]

  val dispositions = List(ApplicationReceived, LoansOriginated, ApprovedButNotAccepted,
    ApplicationsDenied, ApplicationsWithdrawn, ClosedForIncompleteness)

  def generate[ec: EC](
    larSource: TableQuery[LARTable],
    fipsCode: Int
  ): Future[AggregateReportPayload] = {

    val metaData = ReportsMetaDataLookup.values(reportId)

    val lars = filters(larSource)

    val reportDate = formattedCurrentDate

    val smallCounties = CBSATractLookup.smallCounties.map(t => s"${t._1}${t._2}")

    val lowIncomeLars = filterIncomeCharacteristics(lars, 0, 50)
    val moderateIncomeLars = filterIncomeCharacteristics(lars, 50, 80)
    val middleIncomeLars = filterIncomeCharacteristics(lars, 80, 120)
    val upperIncomeLars = filterIncomeCharacteristics(lars, 120, 1000)

    for {
      tractMinorityComposition1 <- dispositionsOutput(filterMinorityPopulation(lars, 0, 10))
      tractMinorityComposition2 <- dispositionsOutput(filterMinorityPopulation(lars, 10, 20))
      tractMinorityComposition3 <- dispositionsOutput(filterMinorityPopulation(lars, 20, 50))
      tractMinorityComposition4 <- dispositionsOutput(filterMinorityPopulation(lars, 50, 80))
      tractMinorityComposition5 <- dispositionsOutput(filterMinorityPopulation(lars, 80, 101))

      lowIncome <- dispositionsOutput(lowIncomeLars)
      moderateIncome <- dispositionsOutput(moderateIncomeLars)
      middleIncome <- dispositionsOutput(middleIncomeLars)
      upperIncome <- dispositionsOutput(upperIncomeLars)

      low1 <- dispositionsOutput(filterMinorityPopulation(lowIncomeLars, 0, 10))
      low2 <- dispositionsOutput(filterMinorityPopulation(lowIncomeLars, 10, 20))
      low3 <- dispositionsOutput(filterMinorityPopulation(lowIncomeLars, 20, 50))
      low4 <- dispositionsOutput(filterMinorityPopulation(lowIncomeLars, 50, 80))
      low5 <- dispositionsOutput(filterMinorityPopulation(lowIncomeLars, 80, 101))

      mod1 <- dispositionsOutput(filterMinorityPopulation(moderateIncomeLars, 0, 10))
      mod2 <- dispositionsOutput(filterMinorityPopulation(moderateIncomeLars, 10, 20))
      mod3 <- dispositionsOutput(filterMinorityPopulation(moderateIncomeLars, 20, 50))
      mod4 <- dispositionsOutput(filterMinorityPopulation(moderateIncomeLars, 50, 80))
      mod5 <- dispositionsOutput(filterMinorityPopulation(moderateIncomeLars, 80, 101))

      mid1 <- dispositionsOutput(filterMinorityPopulation(middleIncomeLars, 0, 10))
      mid2 <- dispositionsOutput(filterMinorityPopulation(middleIncomeLars, 10, 20))
      mid3 <- dispositionsOutput(filterMinorityPopulation(middleIncomeLars, 20, 50))
      mid4 <- dispositionsOutput(filterMinorityPopulation(middleIncomeLars, 50, 80))
      mid5 <- dispositionsOutput(filterMinorityPopulation(middleIncomeLars, 80, 101))

      upp1 <- dispositionsOutput(filterMinorityPopulation(upperIncomeLars, 0, 10))
      upp2 <- dispositionsOutput(filterMinorityPopulation(upperIncomeLars, 10, 20))
      upp3 <- dispositionsOutput(filterMinorityPopulation(upperIncomeLars, 20, 50))
      upp4 <- dispositionsOutput(filterMinorityPopulation(upperIncomeLars, 50, 80))
      upp5 <- dispositionsOutput(filterMinorityPopulation(upperIncomeLars, 80, 101))

      smallCty <- dispositionsOutput(lars.filter(lar => lar.geographyState ++ lar.geographyCounty inSet smallCounties))
      nonSmall <- dispositionsOutput(lars.filterNot(lar => lar.geographyState ++ lar.geographyCounty inSet smallCounties))

      total <- dispositionsOutput(lars)

    } yield {
      val report = s"""
         |{
         |    "table": "${metaData.reportTable}",
         |    "type": "${metaData.reportType}",
         |    "description": "${metaData.description}",
         |    "year": "2017",
         |    "reportDate": "$reportDate",
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
         |                    "dispositions": $lowIncome
         |                },
         |                {
         |                    "income": "Moderate income",
         |                    "dispositions": $moderateIncome
         |                },
         |                {
         |                    "income": "Middle income",
         |                    "dispositions": $middleIncome
         |                },
         |                {
         |                    "income": "Upper income",
         |                    "dispositions": $upperIncome
         |                }
         |            ]
         |        }
         |    ],
         |    "incomeRaces": [
         |        {
         |            "characteristic": "Income & Racial/Ethnic Composition",
         |            "incomes": [
         |                {
         |                    "income": "Low income",
         |                    "compositions": [
         |                        {
         |                            "composition": "Less than 10% minority",
         |                            "dispositions": $low1
         |                        },
         |                        {
         |                            "composition": "10-19% minority",
         |                            "dispositions": $low2
         |                        },
         |                        {
         |                            "composition": "20-49% minority",
         |                            "dispositions": $low3
         |                        },
         |                        {
         |                            "composition": "50-79% minority",
         |                            "dispositions": $low4
         |                        },
         |                        {
         |                            "composition": "80-100% minority",
         |                            "dispositions": $low5
         |                        }
         |                    ]
         |                },
         |                {
         |                    "income": "Moderate income",
         |                    "compositions": [
         |                        {
         |                            "composition": "Less than 10% minority",
         |                            "dispositions": $mod1
         |                        },
         |                        {
         |                            "composition": "10-19% minority",
         |                            "dispositions": $mod2
         |                        },
         |                        {
         |                            "composition": "20-49% minority",
         |                            "dispositions": $mod3
         |                        },
         |                        {
         |                            "composition": "50-79% minority",
         |                            "dispositions": $mod4
         |                        },
         |                        {
         |                            "composition": "80-100% minority",
         |                            "dispositions": $mod5
         |                        }
         |                    ]
         |                },
         |                {
         |                    "income": "Middle income",
         |                    "compositions": [
         |                        {
         |                            "composition": "Less than 10% minority",
         |                            "dispositions": $mid1
         |                        },
         |                        {
         |                            "composition": "10-19% minority",
         |                            "dispositions": $mid2
         |                        },
         |                        {
         |                            "composition": "20-49% minority",
         |                            "dispositions": $mid3
         |                        },
         |                        {
         |                            "composition": "50-79% minority",
         |                            "dispositions": $mid4
         |                        },
         |                        {
         |                            "composition": "80-100% minority",
         |                            "dispositions": $mid5
         |                        }
         |                    ]
         |                },
         |                {
         |                    "income": "Upper income",
         |                    "compositions": [
         |                        {
         |                            "composition": "Less than 10% minority",
         |                            "dispositions": $upp1
         |                        },
         |                        {
         |                            "composition": "10-19% minority",
         |                            "dispositions": $upp2
         |                        },
         |                        {
         |                            "composition": "20-49% minority",
         |                            "dispositions": $upp3
         |                        },
         |                        {
         |                            "composition": "50-79% minority",
         |                            "dispositions": $upp4
         |                        },
         |                        {
         |                            "composition": "80-100% minority",
         |                            "dispositions": $upp5
         |                        }
         |                    ]
         |                }
         |            ]
         |        }
         |    ],
         |    "types": [
         |        {
         |            "type": "Small County",
         |            "dispositions": $smallCty
         |        },
         |        {
         |            "type": "All Other Tracts",
         |            "dispositions": $nonSmall
         |        }
         |    ],
         |    "total": $total
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
