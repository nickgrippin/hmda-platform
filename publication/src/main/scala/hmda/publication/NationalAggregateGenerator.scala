package hmda.publication

import hmda.parser.fi.lar.LarCsvParser
import hmda.publication.reports.aggregate.NationalAggregateA1

import scala.io.Source

object NationalAggregateGenerator {

  def main(args: Array[String]): Unit = {
    val larSource = Source.fromFile("/Users/grippinn/HMDA/hmda-platform/publication/src/main/resources/2018-03-18_lar.txt").getLines.toList
    var count = 0
    var parseCount = 0
    val lars = larSource.collect {
      case l if LarCsvParser(l).isRight => {
        parseCount += 1
        if (parseCount % 100000 == 0) println(s"Parse: $parseCount")
        LarCsvParser(l).right.get
      }
    }.filter(lar => {
      count += 1
      if (count % 100000 == 0) println(s"Filter: $count")
      (1 to 4).contains(lar.loan.loanType)
    })
    println("\n\nLENGTH\n")
    println(lars.length)
    val report = NationalAggregateA1.generateList(lars)
    println(report)
  }
}
