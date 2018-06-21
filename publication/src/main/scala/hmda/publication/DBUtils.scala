package hmda.publication

import hmda._
import hmda.publication.model.LARTable

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Compiled
import hmda.publication.NationalAggregateGenerator._

trait DBUtils {

  def count[ec: EC](input: Query[LARTable, LARTable#TableElementType, Seq]): Future[Int] = {
    val q = for {
      r <- Compiled(input.length).result
    } yield r
    db.run(q)
  }

  def sumLoanAmount[ec: EC](input: Query[LARTable, LARTable#TableElementType, Seq]): Future[Int] = {
    val q = for {
      r <- Compiled(input.map(_.loanAmount).sum).result
    } yield r
    db.run(q).map(opt => opt.getOrElse(-1))
  }

  def sumRateSpread[ec: EC](input: Query[LARTable, LARTable#TableElementType, Seq]): Future[Double] = {
    val q = for {
      r <- Compiled(input.map(_.rateSpread.asColumnOf[Double]).sum).result
    } yield r
    db.run(q).map(opt => opt.getOrElse(0.0))
  }

  def sumWeightedRateSpread[ec: EC](input: Query[LARTable, LARTable#TableElementType, Seq], total: Double): Future[Double] = {
    val q = for {
      r <- Compiled(input.map(lar => (lar.loanAmount.asColumnOf[Double] / total) * (lar.rateSpread.asColumnOf[Double] / 100.0)).sum).result
    } yield r
    db.run(q).map(opt => opt.getOrElse(0.0))
  }
  /*
  def collectHeadValue[T: AS: MAT: EC](input: Source[T, NotUsed]): Future[Try[T]] = {
    input.take(1).runWith(Sink.seq).map(xs => Try(xs.head))
  }

  private def sinkCount[T]: Sink[T, Future[Int]] = {
    Sink.fold[Int, T](0) { (acc, _) =>
      val total = acc + 1
      total
    }
  }

  private def sinkSum[T](summation: T => Int): Sink[T, Future[Int]] = {
    Sink.fold[Int, T](0) { (acc, lar) =>
      val total = acc + summation(lar)
      total
    }
  }

  private def sinkSumDouble[T](summation: T => Double): Sink[T, Future[Double]] = {
    Sink.fold[Double, T](0) { (acc, lar) =>
      val total = acc + summation(lar)
      total
    }
  }*/

  def calculateMean[ec: EC](source: Query[LARTable, LARTable#TableElementType, Seq]): Future[Double] = {
    val loanCountF = count(source)
    val valueSumF = sumRateSpread(source)

    for {
      count <- loanCountF
      totalRateSpread <- valueSumF
    } yield {
      if (count == 0) 0
      else {
        val v = totalRateSpread / count
        BigDecimal(v).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      }
    }
  }

  def calculateWeightedMean[ec: EC](source: Query[LARTable, LARTable#TableElementType, Seq]): Future[Double] = {
    val sL = sumLoanAmount(source)
    for {
      sumLoans <- sL
      sumWeight <- sumWeightedRateSpread(source, sumLoans)
    } yield sumWeight
  }

  def calculateMedian[ec: EC](source: Query[LARTable, LARTable#TableElementType, Seq]): Future[Double] = {
    val q = for {
      all <- Compiled(source.map(_.rateSpread.asColumnOf[Double])).result
    } yield all
    db.run(q).map(calculateMedian(_))
  }

  def calculateMedian(seq: Seq[Double]): Double = {
    if (seq.isEmpty) 0.0
    else if (seq.size == 1) BigDecimal(seq.head).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    else {
      val (lowerHalf, upperHalf) = seq.sortWith(_ < _).splitAt(seq.size / 2)
      val median = if (seq.size % 2 == 0) (lowerHalf.last + upperHalf.head) / 2.0 else upperHalf.head
      BigDecimal(median).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    }
  }

  def calculateWeightedMedian[ec: EC](source: Query[LARTable, LARTable#TableElementType, Seq]): Future[Double] = {
    val mapped = source.map(lar => (lar.loanAmount.asColumnOf[Int], lar.rateSpread.asColumnOf[Double]))
    val q = for {
      all <- Compiled(mapped).result
    } yield all

    db.run(q).map(seq => {
      if (seq.isEmpty) 0
      else if (seq.length == 1) seq.head._2
      else calculateWeightedMedianHelper(seq)
    })
  }

  def calculateWeightedMedianHelper(seq: Seq[(Int, Double)]): Double = {
    val sumLoans = seq.map(i => i._1).sum //Sum of all loans

    val sortedSeq = seq.sortWith((a, b) => a._2 < b._2) //Sort by rate spread
    val weighted = sortedSeq.map(i => (i._1.toDouble / sumLoans, i._2)) //Loan amount weighted by total loan amount

    val w = weightedMedian(weighted, sortedSeq.length / 2)
    BigDecimal(w).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  def weightedMedian(seq: Seq[(Double, Double)], pivot: Int): Double = {
    val seq1 = seq.slice(0, pivot)
    val seq2 = seq.slice(pivot + 1, seq.length)
    val w1 = seq1.map(i => i._1).sum
    val w2 = seq2.map(i => i._1).sum

    if (w1 <= 0.50001 && w2 <= 0.50001)
      seq(pivot)._2
    else if (w1 >= 0.5)
      weightedMedian(seq, pivot - 1)
    else
      weightedMedian(seq, pivot + 1)
  }

}
