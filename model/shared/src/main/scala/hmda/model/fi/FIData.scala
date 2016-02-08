package hmda.model.fi

import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.fi.ts.TransmittalSheet

import scala.scalajs.js.annotation.JSExport

case class FIData(ts: TransmittalSheet, lars: Iterator[LoanApplicationRegister]) {
  def toCSV: String = {
    s"${ts.toCSV}\n" +
      s"${lars.map(lar => lar.toCSV + "\n")}"
  }

  @JSExport("ts")
  def tsJS: TransmittalSheet = ts

}
