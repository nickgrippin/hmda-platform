package hmda.publication.reports.util.db

import hmda.model.publication.reports.EthnicityEnum
import hmda.model.publication.reports.EthnicityEnum._
import hmda.publication.model.LARTable
import slick.jdbc.PostgresProfile.api._

object EthnicityUtilDB {

  def filterEthnicity(larSource: Query[LARTable, LARTable#TableElementType, Seq], ethnicity: EthnicityEnum): Query[LARTable, LARTable#TableElementType, Seq] = {
    ethnicity match {
      case HispanicOrLatino => larSource.filter { lar =>
        lar.applicantEthnicity === 1 &&
          (lar.applicantCoEthnicity === 1 || (lar.applicantCoEthnicity inSet (3 to 5)))
      }
      case NotHispanicOrLatino => larSource.filter { lar =>
        lar.applicantEthnicity === 2 &&
          (lar.applicantCoEthnicity === 2 || (lar.applicantCoEthnicity inSet (3 to 5)))
      }
      case JointEthnicity => larSource.filter { lar =>
        (lar.applicantEthnicity === 1 && lar.applicantCoEthnicity === 2) ||
          (lar.applicantEthnicity === 2 && lar.applicantCoEthnicity === 1)
      }
      case NotAvailable => larSource.filter { lar =>
        lar.applicantEthnicity === 3 || lar.applicantEthnicity === 4
      }
    }
  }
}
