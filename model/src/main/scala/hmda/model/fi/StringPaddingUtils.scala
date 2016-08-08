package hmda.model.fi

trait StringPaddingUtils {
  def padRight(s: String, n: Int): String = {
    String.format("%1$-" + n + "s", s)
  }

  def padLeftWithZero(s: String, n: Int): String = {
    String.format("%1$" + n + "s", s).replace(' ', '0')
  }

  def padNumOrNa(s: String, n: Int): String = {
    if (s == "NA") {
      padRight(s, n)
    } else {
      padLeftWithZero(s, n)
    }
  }
}
