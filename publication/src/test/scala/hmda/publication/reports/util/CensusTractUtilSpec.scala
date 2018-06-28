package hmda.publication.reports.util

import hmda.census.model.{ Tract, TractLookup }
import hmda.model.fi.lar.Geography
import hmda.publication.reports.util.CensusTractUtil._
import hmda.util.SourceUtils
import org.scalacheck.Gen
import org.scalatest.{ AsyncWordSpec, MustMatchers }

class CensusTractUtilSpec extends AsyncWordSpec with MustMatchers with SourceUtils with ApplicantSpecUtil {

  "Tract Lookup" must {
    "find median year homes built" in {
      val tracts = TractLookup.values.filter(_.medianYearHomesBuilt.isDefined).slice(0, 9)
      val lars = genLarsWithTract(tracts)
      val homes = filterMedianYearHomesBuilt(source(lars), 1900, 2000, tracts)
      count(homes).map(_ mustBe 100)
    }
  }

  def genLarsWithTract(tracts: Set[Tract]) = {
    lar100ListGen.sample.get.map { lar =>
      val tract = Gen.oneOf(tracts.toList).sample.get
      val newGeo = Geography(lar.geography.msa, tract.state, tract.county, tract.tractDec)
      lar.copy(geography = newGeo)
    }
  }

}
