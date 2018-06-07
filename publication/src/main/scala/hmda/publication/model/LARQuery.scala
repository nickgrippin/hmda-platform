package hmda.publication.model

case class LARQuery(
  id: String,
  respondentId: String,
  agencyCode: Int,
  loan: LoanQuery,
  preapprovals: Int,
  actionTakenType: Int,
  actionTakenDate: Int,
  geography: GeographyQuery,
  applicant: ApplicantQuery,
  purchaserType: Int,
  denial: DenialQuery,
  rateSpread: String,
  hoepaStatus: Int,
  lienStatus: Int
)

case class LoanQuery(
  loanId: String,
  loanApplicationDate: String,
  loanType: Int,
  loanPropertyType: Int,
  loanPurpose: Int,
  loanOccupancy: Int,
  loanAmount: Int
)

case class GeographyQuery(
  geographyMsa: String,
  geographyState: String,
  geographyCounty: String,
  geographyTract: String
)

case class ApplicantQuery(
  applicantEthnicity: Int,
  applicantCoEthnicity: Int,
  applicantRace1: Int,
  applicantRace2: String,
  applicantRace3: String,
  applicantRace4: String,
  applicantRace5: String,
  applicantCoRace1: Int,
  applicantCoRace2: String,
  applicantCoRace3: String,
  applicantCoRace4: String,
  applicantCoRace5: String,
  applicantSex: Int,
  applicantCoSex: Int,
  applicantIncome: String
)

case class DenialQuery(
  denialReason1: String,
  denialReason2: String,
  denialReason3: String
)
