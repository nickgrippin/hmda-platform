package hmda.publication.reports.util.db

import hmda.model.publication.reports.MinorityStatusEnum.{ OtherIncludingHispanic, WhiteNonHispanic }
import hmda.model.publication.reports._
import hmda.publication.model.LARTable
import slick.jdbc.PostgresProfile.api._

object MinorityStatusUtilDB {

  def filterMinorityStatus(query: Query[LARTable, LARTable#TableElementType, Seq], minorityStatus: MinorityStatusEnum): Query[LARTable, LARTable#TableElementType, Seq] = {
    minorityStatus match {
      case WhiteNonHispanic => query.filter { lar =>
        lar.applicantEthnicity === 2 && lar.applicantRace1 === 5
      }
      case OtherIncludingHispanic => query.filter { lar =>
        lar.applicantEthnicity === 1 && applicantRacesAllNonWhite(lar)
      }
    }
  }

  private def applicantRacesAllNonWhite(lar: LARTable): Rep[Boolean] = {
    val race1NonWhite = lar.applicantRace1 === 1 || lar.applicantRace1 === 2 || lar.applicantRace1 === 3 || lar.applicantRace1 === 4

    race1NonWhite &&
      lar.applicantRace2 =!= "5" &&
      lar.applicantRace3 =!= "5" &&
      lar.applicantRace4 =!= "5" &&
      lar.applicantRace5 =!= "5"
  }

}
