package hmda.publication.model

case class TractQuery(
  id: String,
  msa: Int,
  state: Int,
  county: Int,
  tract: Int,
  minorityPercent: Double,
  tractMfiToMsaPercent: Double,
  medianYearBuilt: Int,
  msaMedIncome: Int
)
