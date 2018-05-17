package hmda.util

import akka.NotUsed
import akka.stream.scaladsl._
import hmda._

import scala.concurrent.Future
import scala.util.Try

trait SourceUtils {

  def count[T: AS: MAT](input: Source[T, NotUsed]): Future[Int] = {
    input.runWith(sinkCount)
  }

  def sum[T: AS: MAT](input: Source[T, NotUsed], summation: T => Int): Future[Int] = {
    input.runWith(sinkSum(summation))
  }

  def weightedSum[T: AS: MAT](input: Source[T, NotUsed], summation: T => Int, total: Int, weight: T => Double): Future[Double] = {
    input.runWith(weightedSinkSum(summation, total, weight))
  }

  def sumDouble[T: AS: MAT](input: Source[T, NotUsed], summation: T => Double): Future[Double] = {
    input.runWith(sinkSumDouble(summation))
  }

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

  private def weightedSinkSum[T](summation: T => Int, totalSum: Int, weight: T => Double): Sink[T, Future[Double]] = {
    Sink.fold[Double, T](0) { (acc, lar) =>
      val weightedValue = summation(lar).toDouble / totalSum * (weight(lar) / 100)
      val total = acc + weightedValue
      total
    }
  }

  private def sinkSumDouble[T](summation: T => Double): Sink[T, Future[Double]] = {
    Sink.fold[Double, T](0) { (acc, lar) =>
      val total = acc + summation(lar)
      total
    }
  }

  def calculateMean[ec: EC, mat: MAT, as: AS, T](source: Source[T, NotUsed], f: T => Double): Future[Double] = {
    val loanCountF = count(source)
    val valueSumF = sumDouble(source, f)

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

  def calculateMedian[ec: EC, mat: MAT, as: AS, T](source: Source[T, NotUsed], f: T => Double): Future[Double] = {
    // If this method encounters collections that are too large and overload memory,
    //   add this to the statement below, with a reasonable limit:
    //   .limit(MAX_SIZE)
    val valuesF: Future[Seq[Double]] = source.map(f).runWith(Sink.seq)

    valuesF.map { seq =>
      if (seq.isEmpty) 0 else calculateMedian(seq)
    }
  }

  def calculateMedian(seq: Seq[Double]): Double = {
    val (lowerHalf, upperHalf) = seq.sortWith(_ < _).splitAt(seq.size / 2)
    val median = if (seq.size % 2 == 0) (lowerHalf.last + upperHalf.head) / 2.0 else upperHalf.head
    BigDecimal(median).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  def calculateWeightedMedian[ec: EC, mat: MAT, as: AS, T](source: Source[T, NotUsed], f: T => (Int, Double)): Future[Double] = {
    // If this method encounters collections that are too large and overload memory,
    //   add this to the statement below, with a reasonable limit:
    //   .limit(MAX_SIZE)
    val valuesF: Future[Seq[(Int, Double)]] = source.map(f).runWith(Sink.seq)

    valuesF.map { seq =>
      if (seq.isEmpty) 0
      else if (seq.length == 1) seq.head._2
      else calculateWeightedMedianHelper(seq)
    }
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
