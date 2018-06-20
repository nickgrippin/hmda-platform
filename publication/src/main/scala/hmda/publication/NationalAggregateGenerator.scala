package hmda.publication

import java.io.{ File, PrintWriter }

import hmda.census.model._
import hmda.parser.fi.lar.LarCsvParser
import hmda.publication.model._
import hmda.publication.reports.aggregate._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.io.Source

object NationalAggregateGenerator {
  implicit val ec = ExecutionContext.global
  val db = Database.forConfig("database")
  val lars = TableQuery[LARTable]
  val tracts = TableQuery[TractTable]

  val tractLookup = TractLookup.values
  val msas = MsaIncomeLookup.values

  def main(args: Array[String]): Unit = {
    val reportList: List[A7X] = List(N71)
    var startTime = System.currentTimeMillis()
    for (report <- reportList) {
      val r = Await.result(report.generate(lars, -1), 5.hours)
      val writer = new PrintWriter(new File(s"${r.reportID}.txt"))
      writer.write(r.report)
      Thread.sleep(10000)
      writer.close()
      val timeDif = (System.currentTimeMillis() - startTime) / 1000 / 60
      println(s"Finished ${r.reportID} in $timeDif minutes")
      startTime = System.currentTimeMillis()
    }
    //loadLarData(args(0).toInt)
    Thread.sleep(10000)
    db.close()
    //val report = NationalAggregateA1.generateList(lars2)
  }

  def loadLarData(start: Int) = {
    val larSource = Source.fromFile("/Users/grippinn/HMDA/hmda-platform/publication/src/main/resources/2018-03-18_lar.txt").getLines.slice(start, start + 1000001).toList
    //Await.result(db.run(lars.schema.create), 1.minute)
    //println("Schema created")
    var count = 0
    var startTime = System.currentTimeMillis()
    larSource.foreach(larString => {
      val lar = LarCsvParser(larString).right.get
      val tract = tractLookup.find(t => t.state == lar.geography.state && t.county == lar.geography.county && t.tractDec == lar.geography.tract).getOrElse(Tract())
      val msa = msas.find(m => m.fips.toString == lar.geography.msa).getOrElse(MsaIncome())
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
        lar.rateSpread, lar.hoepaStatus, lar.lienStatus,
        msa.income, tract.minorityPopulationPercent, tract.tractMfiPercentageOfMsaMfi, tract.medianYearHomesBuilt.getOrElse(-1))

      Await.result(
        db.run(lars.insertOrUpdate(larQuery)), 1.minutes
      )
      count += 1
      val stepLength = 10000
      if (count % stepLength == 0) {
        val timeDif = System.currentTimeMillis() - startTime
        startTime = System.currentTimeMillis()
        val estimatedTimeRemaining = (((timeDif / stepLength) * (14314644 - start - count)) / 3600000).toInt
        val estimatedSectionTimeRemaining = (((timeDif / stepLength) * (1000001 - count)) / 60000).toInt
        println(s"Count : $count\tTime: ${(timeDif / 1000).toInt} sec\tTime left in section: $estimatedSectionTimeRemaining min\tEstimated time remaining: $estimatedTimeRemaining hours")
      }
    })
  }

  def loadTractData() = {
    //Await.result(db.run(tracts.schema.create), 1.minute)
    //println("Schema created")
    var count = 0
    tractLookup.foreach(tract => {
      val msa = msas.find(m => m.fips == tract.msa.toInt).getOrElse(MsaIncome())
      val query = TractQuery(
        s"${tract.msa}-${tract.state}:${tract.county}:${tract.tract}",
        tract.msa,
        tract.state,
        tract.county,
        tract.tractDec,
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
