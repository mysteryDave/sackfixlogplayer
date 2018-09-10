package org.sackfix.logplayer

class SfDecodeLogLineToMsg {

  val SOH_CHAR = 1.toChar
  val eol: Char = '\n'
  val signalCharacters: List[Char] = List('(', ')', '=', '(', ')', ',')
  var signalCount: Int = 0

  /**
    * Simple stateful translator to parse the verbose log string and translate it into a string that the session codec can parse
    * Fails if original message contained brackets () as these are used to enclose the tag number and value in the verbose log
    * @param logChr a character in the log sequence
    * @return a character in the parsable fix string
    */
  def translateLogChar(logChar: Char): Option[Char] = {
    if (logChar == signalCharacters(signalCount)) {
      signalCount = (signalCount + 1) % signalCharacters.length
      logChar match {
        case ','  =>  Option(SOH_CHAR)
        case '='  =>  Option(logChar)
        case _    =>  Option.empty
      }
    }
    else if (signalCount == 1 || signalCount == 3) Option(logChar)
    else Option.empty
  }

  def newLine(): Unit = { signalCount = 0}
}
