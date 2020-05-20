package ledger.command

import ledger.core.Ledger._

import scala.util.matching.Regex
import CmdRegex._


object Command {

  sealed trait Command
  case class CreateAccount(name: Name) extends Command
  case class ProcessTxn(payer: Name, receiver: Name, payload: String, amount: Float)
    extends Command
  case class GetBalance(address: String) extends Command
  case class GetTransaction(txnId: String) extends Command
  case class GetBlock(number: Int) extends Command
  case object UnknownCommand extends Command


  def get(c: String): Command = {
    val tokens = c.split(splitter)
    c match {
      case rCreateAccount(_*)   => CreateAccount(tokens(1))
      case rProcessTxn(_*)      => ProcessTxn(tokens(1), tokens(2), tokens(3), tokens(4).toFloat)
      case rGetBalance(_*)      => GetBalance(tokens(1))
      case rGetBlock(_*)        => GetBlock(tokens(1).toInt)
      case _                    => UnknownCommand
    }
  }
}


object CmdRegex {
  // create-account <account-name>
  val rCreateAccount: Regex = "^create-account \\S+$".r
  // transaction <payer> <receiver> "<payload>" <amount>
  val rProcessTxn: Regex = "^transaction \\S+ \\S+ \"(.*?)\" [0-9]+\\.[0-9]{2}$".r
  // get-balance <account-address>
  val rGetBalance: Regex = "^get-balance \\S+$".r
  // get-block <block-number>
  val rGetBlock: Regex = "^get-block [0-9]+$".r

  // to split between white spaces
  val splitter = "\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)"
}

