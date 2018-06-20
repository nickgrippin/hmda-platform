package hmda.publication.reports.util.db

import hmda.model.publication.reports._
import hmda.publication.DBUtils
import hmda.publication.model.LARTable
import hmda.publication.reports.EC
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

sealed abstract class DispositionTypeDB(
    val value: String,
    val filter: LARTable => Rep[Boolean]
) extends DBUtils {

  def calculateValueDisposition[ec: EC](query: Query[LARTable, LARTable#TableElementType, Seq]): Future[ValueDisposition] = {
    val loansFiltered = query.filter(filter)
    val loanCountF = count(loansFiltered)
    val totalValueF = sumLoanAmount(loansFiltered)
    for {
      count <- loanCountF
      total <- totalValueF
    } yield {
      ValueDisposition(value, count, total)
    }
  }

  def calculatePercentageDisposition[ec: EC](query: Query[LARTable, LARTable#TableElementType, Seq]): Future[PercentageDisposition] = {
    val loansFiltered = query.filter(filter)
    val loanCountF = count(loansFiltered)
    loanCountF.map { count =>
      PercentageDisposition(value, count, 0)
    }
  }
}

object DispositionTypeDB {

  //////////////////////////////////
  // Action Taken Type Dispositions
  //////////////////////////////////

  case object ApplicationReceived extends DispositionTypeDB(
    "Applications Received",
    lar => lar.actionTakenType inSet (1 to 5)
  )
  case object LoansOriginated extends DispositionTypeDB(
    "Loans Originated",
    _.actionTakenType === 1
  )
  case object ApprovedButNotAccepted extends DispositionTypeDB(
    "Apps. Approved But Not Accepted",
    _.actionTakenType === 2
  )
  case object ApplicationsDenied extends DispositionTypeDB(
    "Applications Denied",
    _.actionTakenType === 3
  )
  case object ApplicationsWithdrawn extends DispositionTypeDB(
    "Applications Withdrawn",
    _.actionTakenType === 4
  )
  case object ClosedForIncompleteness extends DispositionTypeDB(
    "Files Closed for Incompleteness",
    _.actionTakenType === 5
  )
  case object LoanPurchased extends DispositionTypeDB(
    "Loan Purchased by your Institution",
    _.actionTakenType === 6
  )
  case object PreapprovalDenied extends DispositionTypeDB(
    "Preapproval Request Denied by Financial Institution",
    _.actionTakenType === 7
  )
  case object PreapprovalApprovedButNotAccepted extends DispositionTypeDB(
    "Preapproval Request Approved but not Accepted by Financial Institution",
    _.actionTakenType === 8
  )

  //////////////////////////////////
  // Purchaser Type Dispositions
  //////////////////////////////////

  case object FannieMae extends DispositionTypeDB(
    "Fannie Mae",
    _.purchaserType === 1
  )
  case object GinnieMae extends DispositionTypeDB(
    "Ginnie Mae",
    _.purchaserType === 2
  )
  case object FreddieMac extends DispositionTypeDB(
    "FreddieMac",
    _.purchaserType === 3
  )
  case object FarmerMac extends DispositionTypeDB(
    "FarmerMac",
    _.purchaserType === 4
  )
  case object PrivateSecuritization extends DispositionTypeDB(
    "Private Securitization",
    _.purchaserType === 5
  )
  case object CommercialBank extends DispositionTypeDB(
    "Commercial bank, savings bank or association",
    _.purchaserType === 6
  )
  case object FinanceCompany extends DispositionTypeDB(
    "Life insurance co., credit union, finance co.",
    _.purchaserType === 7
  )
  case object Affiliate extends DispositionTypeDB(
    "Affiliate institution",
    _.purchaserType === 8
  )
  case object OtherPurchaser extends DispositionTypeDB(
    "Other",
    _.purchaserType === 9
  )

  //////////////////////////////////
  // Denial Reason Dispositions
  //////////////////////////////////

  case object DebtToIncomeRatio extends DispositionTypeDB(
    "Debt-to-Income Ratio",
    _.denialReason1 === "1"
  )
  case object EmploymentHistory extends DispositionTypeDB(
    "Employment History",
    _.denialReason1 === "2"
  )
  case object CreditHistory extends DispositionTypeDB(
    "Credit History",
    _.denialReason1 === "3"
  )
  case object Collateral extends DispositionTypeDB(
    "Collateral",
    _.denialReason1 === "4"
  )
  case object InsufficientCash extends DispositionTypeDB(
    "Insufficient Cash",
    _.denialReason1 === "5"
  )
  case object UnverifiableInformation extends DispositionTypeDB(
    "Unverifiable Information",
    _.denialReason1 === "6"
  )
  case object CreditAppIncomplete extends DispositionTypeDB(
    "Credit App. Incomplete",
    _.denialReason1 === "7"
  )
  case object MortgageInsuranceDenied extends DispositionTypeDB(
    "Mortgage Insurance Denied",
    _.denialReason1 === "8"
  )
  case object OtherDenialReason extends DispositionTypeDB(
    "Other",
    _.denialReason1 === "9"
  )
  case object TotalDenied extends DispositionTypeDB(
    "Total",
    _.denialReason1 =!= ""
  )

  //////////////////////////////////
  // Table 1/2 Dispositions
  //////////////////////////////////
  case object FHA extends DispositionTypeDB(
    "FHA, FSA/RHS & VA (A)",
    lar => (lar.loanPropertyType === 1 || lar.loanPropertyType === 2)
      && (lar.loanPurpose === 1)
      && (lar.loanType inSet (2 to 4))
  )
  case object Conventional extends DispositionTypeDB(
    "Conventional (B)",
    lar => (lar.loanPropertyType === 1 || lar.loanPropertyType === 2)
      && (lar.loanPurpose === 1)
      && (lar.loanType === 1)
  )
  case object Refinancings extends DispositionTypeDB(
    "Refinancings (C)",
    lar => (lar.loanPropertyType === 1 || lar.loanPropertyType === 2)
      && (lar.loanPurpose === 3)
  )
  case object HomeImprovementLoans extends DispositionTypeDB(
    "Home Improvement Loans (D)",
    lar => (lar.loanPropertyType === 1 || lar.loanPropertyType === 2)
      && (lar.loanPurpose === 2)
  )
  case object LoansForFiveOrMore extends DispositionTypeDB(
    "Loans on Dwellings For 5 or More Families (E)",
    lar => lar.loanPropertyType === 3
  )
  case object NonoccupantLoans extends DispositionTypeDB(
    "Nonoccupant Loans (F)",
    lar => lar.loanOccupancy === 2
  )
  case object ManufacturedHomeDwellings extends DispositionTypeDB(
    "Loans On Manufactured Home Dwellings (G)",
    lar => lar.loanPropertyType === 2
  )

  //////////////////////////////////
  // Preapprovals Dispositions
  //////////////////////////////////

  case object PreapprovalsToOriginations extends DispositionTypeDB(
    "Preapprovals reasulting in originations",
    lar => lar.preapprovals === 1 && lar.actionTakenType === 1
  )
  case object PreapprovalsNotAccepted extends DispositionTypeDB(
    "Preapprovals approved but not accepted",
    _.actionTakenType === 8
  )
  case object PreApprovalsDenied extends DispositionTypeDB(
    "Preapprovals denied",
    _.actionTakenType === 7
  )
}
