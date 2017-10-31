package hmda.model.publication.reports

import enumeratum.values.{ IntEnum, IntEnumEntry }

sealed abstract class DispositionEnum(
  override val value: Int,
  val description: String
) extends IntEnumEntry

object DispositionEnum extends IntEnum[DispositionEnum] {

  val values = findValues

  case object ApplicationReceived extends DispositionEnum(0, "Application Received")
  case object LoansOriginated extends DispositionEnum(1, "Loans Originated")
  case object ApprovedButNotAccepted extends DispositionEnum(2, "Apps. Approved But Not Accepted")
  case object ApplicationsDenied extends DispositionEnum(3, "Applications Denied")
  case object ApplicationsWithdrawn extends DispositionEnum(4, "Applications Withdrawn")
  case object ClosedForIncompleteness extends DispositionEnum(5, "Files Closed for Incompleteness")
  case object LoanPurchased extends DispositionEnum(6, "Loan Purchased by your Institution")
  case object PreapprovalDenied extends DispositionEnum(7, "Preapproval Request Denied by Financial Institution")
  case object PreapprovalApprovedButNotAccepted extends DispositionEnum(8, "Preapproval Request Approved but not Accepted by Financial Institution")

  case object FannieMae extends DispositionEnum(1, "Fannie Mae")
  case object GinnieMae extends DispositionEnum(2, "Ginnie Mae")
  case object FreddieMac extends DispositionEnum(3, "FreddieMac")
  case object FarmerMac extends DispositionEnum(4, "FarmerMac")
  case object PrivateSecuritization extends DispositionEnum(5, "Private Securitization")
  case object CommercialBank extends DispositionEnum(6, "Commercial bank, savings bank or association")
  case object FinanceCompany extends DispositionEnum(7, "Life insurance co., credit union, finance co.")
  case object Affiliate extends DispositionEnum(8, "Affiliate institution")
  case object OtherPurchaser extends DispositionEnum(9, "Other")
}
