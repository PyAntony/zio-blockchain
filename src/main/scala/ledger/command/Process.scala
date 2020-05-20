package ledger.command

import ledger.command.Command._
import ledger.command.Process._
import ledger.core._
import ledger.core.Ledger._
import ledger.core.Components._

import zio._
import zio.console._
import zio.clock._


final case class Process(c: Command) {
  def process(ledger: Ledger): URIO[Console with Clock, Response] = c match {

    case CreateAccount(name) =>
      ledger.createAccount(name).either.map(response(c, _, "Address: "))

    case ProcessTxn(p, r, payload, amount) =>
      ledger.processTxn(p, r, payload, amount).either.map(response(c, _, "Id: "))

    case GetBalance(address) =>
      ledger.accounts.zmap.get(address).commit
        .filterOrFail(_.isDefined)(InvalidAccount("Address not found"))
        .map(_.get.balance.toString).either
        .map(response(c, _, "Balance: "))

    case GetBlock(number) =>
      ledger.blocks.zmap.get(number).commit
        .filterOrFail(_.isDefined)(InvalidBlock("Block not found"))
        .map(o => blockToString(o.getOrElse(emptyBlock))).either
        .map(response(c, _, ""))

    case UnknownCommand => UIO (
      response(c, Left(UnknownCommandException("???")), "")
    )
  }
}


object Process {
  implicit def toProcess(c: Command): Process = Process(c)

  final case class Response(command: Command, message: String)

  def response(c: Command, e: Either[Exception, String], r: String): Response =
    Response(c, e match {
      case Left(exc) => s"Failure >> ${exc.getClass.getSimpleName + ": " + exc.getMessage}"
      case Right(_)  => s"Success >> ${r + e.getOrElse("")}"
    }
  )
}



