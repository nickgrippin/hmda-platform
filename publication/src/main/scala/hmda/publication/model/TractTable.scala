package hmda.publication.model

import slick.jdbc.PostgresProfile.api._

class TractTable(tag: Tag) extends Table[TractQuery](tag, "tract") {
  def id = column[String]("id", O.PrimaryKey)
  def msa = column[String]("msa")
  def state = column[String]("state")
  def county = column[String]("county")
  def tract = column[String]("tract")
  def minorityPercent = column[Double]("minorityPercent")
  def tractMfiToMsaPercent = column[Double]("tractMfiToMsaPercent")
  def medianYearBuilt = column[Int]("medianYearBuilt")
  def msaMedIncome = column[Int]("msaMedIncome")

  def * = (id, msa, state, county, tract, minorityPercent,
    tractMfiToMsaPercent, medianYearBuilt, msaMedIncome) <>
    (TractQuery.tupled, TractQuery.unapply(_))
}
