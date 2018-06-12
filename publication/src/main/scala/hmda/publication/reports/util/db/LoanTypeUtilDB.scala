package hmda.publication.reports.util.db

import hmda.publication.DBUtils
import hmda.publication.model.LARTable
import hmda.publication.reports._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

object LoanTypeUtilDB extends DBUtils {
  def loanTypes[ec: EC](query: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    for {
      conv <- purposesOutput(query.filter(lar => lar.loanType === 1))
      fha <- purposesOutput(query.filter(lar => lar.loanType === 2))
      va <- purposesOutput(query.filter(lar => lar.loanType === 3))
      fsa <- purposesOutput(query.filter(lar => lar.loanType === 4))
    } yield {
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
  }

  private def purposesOutput[ec: EC](query: Query[LARTable, LARTable#TableElementType, Seq]): Future[String] = {
    for {
      homePurchaseFirst <- count(query.filter(lar => lar.lienStatus === 1 && lar.loanPurpose === 1))
      homePurchaseJunior <- count(query.filter(lar => lar.lienStatus === 2 && lar.loanPurpose === 1))
      refinanceFirst <- count(query.filter(lar => lar.lienStatus === 1 && lar.loanPurpose === 3))
      refinanceJunior <- count(query.filter(lar => lar.lienStatus === 2 && lar.loanPurpose === 3))
      homeImprovementFirst <- count(query.filter(lar => lar.lienStatus === 1 && lar.loanPurpose === 2))
      homeImprovementJunior <- count(query.filter(lar => lar.lienStatus === 2 && lar.loanPurpose === 2))
      homeImprovementNo <- count(query.filter(lar => lar.lienStatus =!= 1 && lar.lienStatus =!= 2 && lar.loanPurpose === 2))
    } yield {
      s"""
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
    }
  }
}
