package hmda.publication.reports.util

import hmda.model.fi.lar.LoanApplicationRegister
import hmda.publication.reports._
import hmda.util.SourceUtils

import scala.concurrent.Future

object LoanTypeUtilDB extends SourceUtils {
  def loanTypesList(larSource: List[LoanApplicationRegister]): String = {
    println("Calling loan types")
    val conv = purposesOutputList(larSource.filter(lar => lar.loan.loanType == 1))
    val fha = purposesOutputList(larSource.filter(lar => lar.loan.loanType == 2))
    val va = purposesOutputList(larSource.filter(lar => lar.loan.loanType == 3))
    val fsa = purposesOutputList(larSource.filter(lar => lar.loan.loanType == 4))

    s"""
       |[
       |  {
       |    "loanType": "Conventional",
       |    "purposes": $conv
       |  },
       |  {
       |    "loanType": "FHA",
       |    "purposes": $fha
       |  },
       |  {
       |    "loanType": "VA",
       |    "purposes": $va
       |  },
       |  {
       |    "loanType": "FSA/RHS",
       |    "purposes": $fsa
       |  }
       |]
   """.stripMargin
  }

  private def purposesOutputList(larSource: List[LoanApplicationRegister]): String = {
    println("Calling Purposes Output")
    val homePurchaseFirst = larSource.count(lar => lar.lienStatus == 1 && lar.loan.purpose == 1)
    println(s"HPF: $homePurchaseFirst")
    val homePurchaseJunior = larSource.count(lar => lar.lienStatus == 2 && lar.loan.purpose == 1)
    println(s"HPJ: $homePurchaseJunior")
    val refinanceFirst = larSource.count(lar => lar.lienStatus == 1 && lar.loan.purpose == 3)
    println(s"RF: $refinanceFirst")
    val refinanceJunior = larSource.count(lar => lar.lienStatus == 2 && lar.loan.purpose == 3)
    println(s"RJ: $refinanceJunior")
    val homeImprovementFirst = larSource.count(lar => lar.lienStatus == 1 && lar.loan.purpose == 2)
    println(s"HIF: $homeImprovementFirst")
    val homeImprovementJunior = larSource.count(lar => lar.lienStatus == 2 && lar.loan.purpose == 2)
    println(s"HIJ: $homeImprovementJunior")
    val homeImprovementNo = larSource.count(lar => lar.lienStatus != 1 && lar.lienStatus != 2 && lar.loan.purpose == 2)
    println(s"HIN: $homeImprovementNo")

    val s = s"""
         |[
         |  {
         |    "purpose": "Home Purchase",
         |    "firstLienCount": $homePurchaseFirst,
         |    "juniorLienCount": $homePurchaseJunior
         |  },
         |  {
         |    "purpose": "Refinance",
         |    "firstLienCount": $refinanceFirst,
         |    "juniorLienCount": $refinanceJunior
         |  },
         |  {
         |    "purpose": "Home Improvement",
         |    "firstLienCount": $homeImprovementFirst,
         |    "juniorLienCount": $homeImprovementJunior,
         |    "noLienCount": $homeImprovementNo
         |  }
         |]
     """.stripMargin
    println(s)
    s
  }
}
