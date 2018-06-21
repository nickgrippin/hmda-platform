package hmda.publication.reports.util.db

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.census.CBSATractLookup
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.publication.model.LARTable
import slick.jdbc.PostgresProfile.api._

object CensusTractUtilDB {

  def filterMinorityPopulation(lars: Query[LARTable, LARTable#TableElementType, Seq], lower: Double, upper: Double): Query[LARTable, LARTable#TableElementType, Seq] = {
    lars.filter(lar => lar.minorityPercent >= lower && lar.minorityPercent < upper)
  }

  def filterIncomeCharacteristics(lars: Query[LARTable, LARTable#TableElementType, Seq], lower: Double, upper: Double): Query[LARTable, LARTable#TableElementType, Seq] = {
    lars.filter(lar => lar.tractMfiToMsaPercent >= lower && lar.tractMfiToMsaPercent < upper)
  }

  def filterMedianYearHomesBuilt(lars: Query[LARTable, LARTable#TableElementType, Seq], lower: Int, upper: Int): Query[LARTable, LARTable#TableElementType, Seq] = {
    lars.filter(lar => lar.medianYearBuilt >= lower && lar.medianYearBuilt < upper)
  }

  def filterUnknownMedianYearBuilt(lars: Query[LARTable, LARTable#TableElementType, Seq]): Query[LARTable, LARTable#TableElementType, Seq] = {
    lars.filter { lar => lar.medianYearBuilt < 0 }
  }

  def filterSmallCounty(lars: Source[LoanApplicationRegister, NotUsed]): Source[LoanApplicationRegister, NotUsed] = {
    lars.filter(lar => CBSATractLookup.geoIsSmallCounty(lar.geography))
  }

  def filterNotSmallCounty(lars: Source[LoanApplicationRegister, NotUsed]): Source[LoanApplicationRegister, NotUsed] = {
    lars.filterNot(lar => CBSATractLookup.geoIsSmallCounty(lar.geography))
  }

}
