package hmda.publication.lar.model

import hmda.model.filing.lar._
import hmda.model.filing.lar.enums._

import scala.language.implicitConversions

case class Msa(
  id: String = "",
  name: String = "",
  totalLars: Int = 0,
  totalAmount: Double = 0,
  conv: Int = 0,
  FHA: Int = 0,
  VA: Int = 0,
  FSA: Int = 0,
  siteBuilt: Int = 0,
  manufactured: Int = 0,
  oneToFour: Int = 0,
  fivePlus: Int = 0,
  homePurchase: Int = 0,
  homeImprovement: Int = 0,
  refinancing: Int = 0,
  cashOutRefinancing: Int = 0,
  otherPurpose: Int = 0,
  notApplicablePurpose: Int = 0
) {
  def addLar(lar: LoanApplicationRegister): Msa = {
    implicit def bool2int(b: Boolean): Int = if (b) 1 else 0

    Msa(
      id = id,
      name = name,
      totalLars = totalLars + 1,
      totalAmount = totalAmount + lar.loan.amount,
      conv = conv + (lar.loan.loanType == Conventional),
      FHA = FHA + (lar.loan.loanType == FHAInsured),
      VA = VA + (lar.loan.loanType == VAGuaranteed),
      FSA = FSA + (lar.loan.loanType == RHSOrFSAGuaranteed),
      siteBuilt = siteBuilt + (lar.loan.constructionMethod == SiteBuilt),
      manufactured = manufactured + (lar.loan.constructionMethod == ManufacturedHome),
      oneToFour = oneToFour + (lar.property.totalUnits <= 4),
      fivePlus = fivePlus + (lar.property.totalUnits >= 5),
      homePurchase = homePurchase + (lar.loan.loanPurpose == HomePurchase),
      homeImprovement = homeImprovement + (lar.loan.loanPurpose == HomeImprovement),
      refinancing = refinancing + (lar.loan.loanPurpose == Refinancing),
      cashOutRefinancing = cashOutRefinancing + (lar.loan.loanPurpose == CashOutRefinancing),
      otherPurpose = otherPurpose + (lar.loan.loanPurpose == OtherPurpose),
      notApplicablePurpose = notApplicablePurpose + (lar.loan.loanPurpose == LoanPurposeNotApplicable)
    )
  }

  def toCsv: String = s"""$id,\"$name\", $totalLars, $totalAmount, $conv, $FHA, $VA, $FSA, $siteBuilt, $manufactured, $oneToFour, $fivePlus, $homePurchase, $homeImprovement, $refinancing, $cashOutRefinancing, $otherPurpose, $notApplicablePurpose"""
}

case class MsaMap(
 msas: Map[String, Msa] = Map[String, Msa]()
) {
  def +(lar: LoanApplicationRegister): MsaMap = {
    val id = ""
    val original = msas.getOrElse(id, Msa(id, ""))
    val modified = original.addLar(lar)
    MsaMap(msas + ((id, modified)))
  }
}

case class MsaSummary(
 lars: Int = 0,
 amount: Double = 0,
 conv: Int = 0,
 FHA: Int = 0,
 VA: Int = 0,
 FSA: Int = 0,
 siteBuilt: Int = 0,
 manufactured: Int = 0,
 oneToFour: Int = 0,
 fivePlus: Int = 0,
 homePurchase: Int = 0,
 homeImprovement: Int = 0,
 refinancing: Int = 0,
 cashOutRefinancing: Int = 0,
 otherPurpose: Int = 0,
 notApplicablePurpose: Int = 0
) {
  def +(elem: Msa): MsaSummary = {
    new MsaSummary(
      lars + elem.totalLars,
      amount + elem.totalAmount,
      conv + elem.conv,
      FHA + elem.FHA,
      VA + elem.VA,
      FSA + elem.FSA,
      siteBuilt + elem.siteBuilt,
      manufactured + elem.manufactured,
      oneToFour + elem.oneToFour,
      fivePlus + elem.fivePlus,
      homePurchase + elem.homePurchase,
      homeImprovement + elem.homeImprovement,
      refinancing + elem.refinancing,
      cashOutRefinancing + elem.cashOutRefinancing,
      otherPurpose + elem.otherPurpose,
      notApplicablePurpose + elem.notApplicablePurpose
    )
  }

  def toCsv: String = s"Totals,, $lars, $amount, $conv, $FHA, $VA, $FSA, $siteBuilt, $manufactured, $oneToFour, $fivePlus, $homePurchase, $homeImprovement, $refinancing, $cashOutRefinancing, $otherPurpose, $notApplicablePurpose"
}

case object MsaSummary {
  def fromMsaCollection(msas: Seq[Msa]): MsaSummary = {
    msas.foldLeft(MsaSummary()) { (summary, msa) => summary + msa }
  }
}
