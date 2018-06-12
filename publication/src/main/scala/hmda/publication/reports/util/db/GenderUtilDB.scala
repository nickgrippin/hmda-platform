package hmda.publication.reports.util.db

import hmda.model.publication.reports.GenderEnum
import hmda.model.publication.reports.GenderEnum._
import hmda.publication.model.LARTable
import slick.jdbc.PostgresProfile.api._

object GenderUtilDB {

  def filterGender(larSource: Query[LARTable, LARTable#TableElementType, Seq], genderEnum: GenderEnum): Query[LARTable, LARTable#TableElementType, Seq] = {
    genderEnum match {
      case Male => larSource.filter(isMale)
      case Female => larSource.filter(isFemale)
      case JointGender => larSource.filter(isJoint)
      case GenderNotAvailable => larSource.filter(isNotAvailable)
    }
  }

  private def isMale(lar: LARTable): Rep[Boolean] =
    lar.applicantSex === 1 && lar.applicantCoSex =!= 2

  private def isFemale(lar: LARTable): Rep[Boolean] =
    lar.applicantSex === 2 && lar.applicantCoSex =!= 1

  private def isJoint(lar: LARTable): Rep[Boolean] =
    (lar.applicantSex === 1 && lar.applicantCoSex === 2) ||
      (lar.applicantSex === 2 && lar.applicantCoSex === 1)

  private def isNotAvailable(lar: LARTable): Rep[Boolean] =
    lar.applicantSex === 3

}
