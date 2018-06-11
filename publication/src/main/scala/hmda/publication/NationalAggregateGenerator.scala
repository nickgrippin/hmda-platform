package hmda.publication

import hmda.parser.fi.lar.LarCsvParser
import hmda.publication.model._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

object NationalAggregateGenerator {

  def main(args: Array[String]): Unit = {
    val larSource = Source.fromFile("/Users/grippinn/HMDA/hmda-platform/publication/src/main/resources/2018-03-18_lar.txt").getLines.slice(4500000, 6500001).toList
    val db = Database.forConfig("database")
    val lars = TableQuery[LARTable]
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
    println("Finished!")
    Thread.sleep(10000)
    db.close()
    //val report = NationalAggregateA1.generateList(lars2)
  }
}
