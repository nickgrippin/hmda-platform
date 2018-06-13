package hmda.publication.model

case class TractQuery(
  id: String,
  msa: String,
  state: String,
  county: String,
  tract: String,
  minorityPercent: Double,
  tractMfiToMsaPercent: Double,
  medianYearBuilt: Int,
  msaMedIncome: Int
)
