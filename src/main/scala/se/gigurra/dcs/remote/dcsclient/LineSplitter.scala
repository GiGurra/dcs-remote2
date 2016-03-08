package se.gigurra.dcs.remote.dcsclient

class LineSplitter {

  private val buffer = new StringBuilder()

  def apply(data: String): Seq[String] = {
    buffer
      .append(data)
      .lastIndexOf("\n") + 1 match {
        case nComplete if nComplete > 0 =>
          val out = buffer.substring(0, nComplete).split("\n")
          buffer.delete(0, nComplete)
          out
        case _ => Nil
      }
  }
  
  def clear() {
    buffer.clear()
  }
  
}