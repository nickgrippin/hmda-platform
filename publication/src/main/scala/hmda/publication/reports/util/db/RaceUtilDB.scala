package hmda.publication.reports.util.db

import hmda.model.publication.reports.RaceEnum
import hmda.model.publication.reports.RaceEnum._
import hmda.publication.model.LARTable
import slick.jdbc.PostgresProfile.api._

object RaceUtilDB {

  def filterRace(query: Query[LARTable, LARTable#TableElementType, Seq], race: RaceEnum): Query[LARTable, LARTable#TableElementType, Seq] = {
    race match {
      case AmericanIndianOrAlaskaNative =>
        query.filter { lar =>
          lar.applicantRace1 === 1 && coApplicantNonWhite(lar) &&
            (applicantRace2Thru5Blank(lar) || lar.applicantRace2 === "5")
        }

      case Asian =>
        query.filter { lar =>
          lar.applicantRace1 === 2 && coApplicantNonWhite(lar) &&
            (applicantRace2Thru5Blank(lar) || lar.applicantRace2 === "5")
        }

      case BlackOrAfricanAmerican =>
        query.filter { lar =>
          lar.applicantRace1 === 3 && coApplicantNonWhite(lar) &&
            (applicantRace2Thru5Blank(lar) || lar.applicantRace2 === "5")
        }

      case HawaiianOrPacific =>
        query.filter { lar =>
          lar.applicantRace1 === 4 && coApplicantNonWhite(lar) &&
            (applicantRace2Thru5Blank(lar) || lar.applicantRace2 === "5")
        }

      case White =>
        query.filter { lar =>
          lar.applicantRace1 === 5 && applicantRace2Thru5Blank(lar) && coApplicantNonMinority(lar)
        }

      case TwoOrMoreMinority =>
        query.filter(lar => applicantTwoOrMoreMinorities(lar) && coApplicantNonWhite(lar))

      case JointRace =>
        query.filter { lar =>
          (applicantOneOrMoreMinorities(lar) || coApplicantOneOrMoreMinorities(lar)) &&
            (applicantWhite(lar) || coApplicantWhite(lar))
        }

      case NotProvided =>
        query.filter(lar => lar.applicantRace1 === 6 || lar.applicantRace1 === 7)

    }
  }

  private def applicantRace2Thru5Blank(lar: LARTable): Rep[Boolean] = {
    lar.applicantRace2 === "" &&
      lar.applicantRace3 === "" &&
      lar.applicantRace4 === "" &&
      lar.applicantRace5 === ""
  }

  private def applicantWhite(lar: LARTable): Rep[Boolean] = {
    lar.applicantRace1 === 5 &&
      lar.applicantRace2 === "" &&
      lar.applicantRace3 === "" &&
      lar.applicantRace4 === "" &&
      lar.applicantRace5 === ""
  }

  private def coApplicantWhite(lar: LARTable): Rep[Boolean] = {
    lar.applicantCoRace1 === 5 &&
      lar.applicantCoRace2 === "" &&
      lar.applicantCoRace3 === "" &&
      lar.applicantCoRace4 === "" &&
      lar.applicantCoRace5 === ""
  }

  private def coApplicantNonWhite(lar: LARTable): Rep[Boolean] = {
    lar.applicantCoRace1 =!= 5 &&
      lar.applicantCoRace2 =!= "5" &&
      lar.applicantCoRace3 =!= "5" &&
      lar.applicantCoRace4 =!= "5" &&
      lar.applicantCoRace5 =!= "5"
  }

  private def coApplicantNonMinority(lar: LARTable): Rep[Boolean] = {
    val race1NonMinority = lar.applicantCoRace1 === 5 || lar.applicantCoRace1 === 6 || lar.applicantCoRace1 === 7 || lar.applicantCoRace1 === 8
    race1NonMinority &&
      (lar.applicantRace2 === "5" || lar.applicantRace2 === "") &&
      (lar.applicantRace3 === "5" || lar.applicantRace3 === "") &&
      (lar.applicantRace4 === "5" || lar.applicantRace4 === "") &&
      (lar.applicantRace5 === "5" || lar.applicantRace5 === "")
  }

  private def applicantTwoOrMoreMinorities(lar: LARTable): Rep[Boolean] = {
    lar.applicantRace1 =!= 5 &&
      ((lar.applicantRace2 =!= "" && lar.applicantRace2 =!= "5") ||
        (lar.applicantRace3 =!= "" && lar.applicantRace3 =!= "5") ||
        (lar.applicantRace4 =!= "" && lar.applicantRace4 =!= "5") ||
        (lar.applicantRace5 =!= "" && lar.applicantRace5 =!= "5"))
  }

  private def applicantOneOrMoreMinorities(lar: LARTable): Rep[Boolean] = {
    (lar.applicantRace1 === 1 || lar.applicantRace1 === 2 || lar.applicantRace1 === 3 || lar.applicantRace1 === 4) ||
      (lar.applicantRace2 =!= "" && lar.applicantRace2 =!= "5") ||
      (lar.applicantRace3 =!= "" && lar.applicantRace3 =!= "5") ||
      (lar.applicantRace4 =!= "" && lar.applicantRace4 =!= "5") ||
      (lar.applicantRace5 =!= "" && lar.applicantRace5 =!= "5")
  }
  private def coApplicantOneOrMoreMinorities(lar: LARTable): Rep[Boolean] = {
    (lar.applicantCoRace1 === 1 || lar.applicantCoRace1 === 2 || lar.applicantCoRace1 === 3 || lar.applicantCoRace1 === 4) ||
      (lar.applicantCoRace2 =!= "" && lar.applicantCoRace2 =!= "5") ||
      (lar.applicantCoRace3 =!= "" && lar.applicantCoRace3 =!= "5") ||
      (lar.applicantCoRace4 =!= "" && lar.applicantCoRace4 =!= "5") ||
      (lar.applicantCoRace5 =!= "" && lar.applicantCoRace5 =!= "5")
  }

}
