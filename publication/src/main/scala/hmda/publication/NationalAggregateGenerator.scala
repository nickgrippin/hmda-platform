package hmda.publication

import hmda.census.model.{ MsaIncome, MsaIncomeLookup, TractLookup }
import hmda.parser.fi.lar.LarCsvParser
import hmda.publication.model._
import hmda.publication.reports.aggregate.{ NationalAggregateA1, NationalAggregateA2, NationalAggregateA3 }
import hmda.publication.reports.util.CensusTractUtil
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.io.Source

object NationalAggregateGenerator {
  implicit val ec = ExecutionContext.global
  val db = Database.forConfig("database")
  val lars = TableQuery[LARTable]
  val tracts = TableQuery[TractTable]

  def main(args: Array[String]): Unit = {
    val report = Await.result(NationalAggregateA1.generate(lars, -1), 5.hours)
    println(s"Finished!\n\n${report.report}")
    Thread.sleep(10000)
    db.close()
    //val report = NationalAggregateA1.generateList(lars2)
  }

  def loadLarData() = {
    val larSource = Source.fromFile("/Users/grippinn/HMDA/hmda-platform/publication/src/main/resources/2018-03-18_lar.txt").getLines.slice(4500000, 6500001).toList
    //Await.result(db.run(lars.schema.create), 1.minute)
    //println("Schema created")
    var count = 0
    larSource.foreach(larString => {
      val lar = LarCsvParser(larString).right.get
      val larId = lar.loan.id + lar.agencyCode + lar.respondentId
      val loanQuery = LoanQuery(lar.loan.id, lar.loan.applicationDate, lar.loan.loanType, lar.loan.propertyType, lar.loan.purpose, lar.loan.occupancy, lar.loan.amount)
      val geographyQuery = GeographyQuery(lar.geography.msa, lar.geography.state, lar.geography.county, lar.geography.tract)
      val applicantQuery = ApplicantQuery(lar.applicant.ethnicity, lar.applicant.coEthnicity, lar.applicant.race1, lar.applicant.race2, lar.applicant.race3,
        lar.applicant.race4, lar.applicant.race5, lar.applicant.coRace1, lar.applicant.coRace2, lar.applicant.coRace3, lar.applicant.coRace4, lar.applicant.coRace5,
        lar.applicant.sex, lar.applicant.coSex, lar.applicant.income)
      val denialQuery = DenialQuery(lar.denial.reason1, lar.denial.reason2, lar.denial.reason3)
      val larQuery = LARQuery(larId, lar.respondentId, lar.agencyCode, loanQuery, lar.preapprovals,
        lar.actionTakenType, lar.actionTakenDate, geographyQuery,
        applicantQuery, lar.purchaserType, denialQuery,
        lar.rateSpread, lar.hoepaStatus, lar.lienStatus)
      Await.result(
        db.run(
          DBIO.seq(
            lars.insertOrUpdate(larQuery)
          )
        ), 1.minutes
      )
      count += 1
      if (count % 100000 == 0) println(s"Count : $count")
    })
  }

  def loadTractData = {
    val msas = MsaIncomeLookup.values
    val tractLookup = TractLookup.values
    var count = 0
    tractLookup.foreach(tract => {
      val msa = msas.find(m => m.fips == tract.msa.toInt).getOrElse(MsaIncome())
      val query = TractQuery(
        s"${tract.msa}-${tract.state}:${tract.county}:${tract.tract}",
        tract.msa.toInt,
        tract.state.toInt,
        tract.county.toInt,
        tract.tract.toInt,
        tract.minorityPopulationPercent,
        tract.tractMfiPercentageOfMsaMfi,
        2015 - tract.medianYearHomesBuilt.getOrElse(2016),
        msa.income
      )
      Await.result(db.run(tracts.insertOrUpdate(query)), 1.minutes)
      count += 1
      if (count % 10000 == 0) println(count)
    })
  }
}
