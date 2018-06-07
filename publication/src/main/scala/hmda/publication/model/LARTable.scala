package hmda.publication.model

import slick.jdbc.PostgresProfile.api._

class LARTable(tag: Tag) extends Table[LARQuery](tag, "lar") {
  //39
  def id = column[String]("id", O.PrimaryKey)
  def respondentId = column[String]("respondentId")
  def agencyCode = column[Int]("agencyCode")
  def loanId = column[String]("loanId")
  def loanApplicationDate = column[String]("loanApplicationDate")
  def loanType = column[Int]("loanType")
  def loanPropertyType = column[Int]("loanPropertyType")
  def loanPurpose = column[Int]("loanPurpose")
  def loanOccupancy = column[Int]("loanOccupancy")
  def loanAmount = column[Int]("loanAmount")
  def preapprovals = column[Int]("preapprovals")
  def actionTakenType = column[Int]("actionTakenType")
  def actionTakenDate = column[Int]("actionTakenDate")
  def geographyMsa = column[String]("geographyMsa")
  def geographyState = column[String]("geographyState")
  def geographyCounty = column[String]("geographyCounty")
  def geographyTract = column[String]("geographyTract")
  def applicantEthnicity = column[Int]("applicantEthnicity")
  def applicantCoEthnicity = column[Int]("applicantCoEthnicity")
  def applicantRace1 = column[Int]("applicantRace1")
  def applicantRace2 = column[String]("applicantRace2")
  def applicantRace3 = column[String]("applicantRace3")
  def applicantRace4 = column[String]("applicantRace4")
  def applicantRace5 = column[String]("applicantRace5")
  def applicantCoRace1 = column[Int]("applicantCoRace1")
  def applicantCoRace2 = column[String]("applicantCoRace2")
  def applicantCoRace3 = column[String]("applicantCoRace3")
  def applicantCoRace4 = column[String]("applicantCoRace4")
  def applicantCoRace5 = column[String]("applicantCoRace5")
  def applicantSex = column[Int]("applicantSex")
  def applicantCoSex = column[Int]("applicantCoSex")
  def applicantIncome = column[String]("applicantIncome")
  def purchaserType = column[Int]("purchaserType")
  def denialReason1 = column[String]("denialReason1")
  def denialReason2 = column[String]("denialReason2")
  def denialReason3 = column[String]("denialReason3")
  def rateSpread = column[String]("rateSpread")
  def hoepaStatus = column[Int]("hoepaStatus")
  def lienStatus = column[Int]("lienStatus")

  //16
  def * = (id, respondentId, agencyCode, loanProjection, preapprovals, actionTakenType, actionTakenDate,
    geographyProjection, applicantProjection, purchaserType, denialProjection, rateSpread, hoepaStatus,
    lienStatus) <> ((LARQuery.apply _).tupled, LARQuery.unapply)

  //7
  def loanProjection = (loanId, loanApplicationDate, loanType, loanPropertyType, loanPurpose,
    loanOccupancy, loanAmount) <> ((LoanQuery.apply _).tupled, LoanQuery.unapply)

  //4
  def geographyProjection = (geographyMsa, geographyState, geographyCounty,
    geographyTract) <> ((GeographyQuery.apply _).tupled, GeographyQuery.unapply)

  def denialProjection = (denialReason1, denialReason2, denialReason3) <> ((DenialQuery.apply _).tupled, DenialQuery.unapply)

  //15
  def applicantProjection = (applicantEthnicity, applicantCoEthnicity, applicantRace1, applicantRace2, applicantRace3, applicantRace4,
    applicantRace5, applicantCoRace1, applicantCoRace2, applicantCoRace3, applicantCoRace4, applicantCoRace5, applicantSex,
    applicantCoSex, applicantIncome) <> ((ApplicantQuery.apply _).tupled, ApplicantQuery.unapply)
}
