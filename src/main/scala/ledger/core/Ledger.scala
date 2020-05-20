package ledger.core

import java.security.MessageDigest

import ledger.core.Components._
import ledger.core.Ledger._
import zio.duration._
import zio.clock._
import zio.console._
import zio.stm._
import zio._


final case class Ledger private(accounts: ZMap[Name, Account],
                                blocks: ZMap[Counter, Block],
                                transactions: ZMap[Id, Transaction],
                                txnPerBlock: Int,
                                txnFeePct: Float,
                                initialTxn: Int,
                                masterAcc: Name,
                                seed: String) {


  def createAccount(nickName: String): ZIO[Clock, Exception, String] =
      STM.atomically {
        for {
          _        <- accounts.zmap.get(nickName)
                        .filterOrFail(_.isEmpty)(InvalidAccount("Nickname exists"))


          accNum   <- accounts.counter.updateAndGet(_ + 1)
          address  <- STM.succeed(Ledger.generateUUID(seed + accNum))

          acc  = Account(nickName, address, 0)
          _   <- accounts.zmap.put(acc.nickName, acc)
        } yield address
      } retry Schedule.duration(3.seconds)


  def processTxn(p: Address, r: Address, payload: String, amount: Float):
  ZIO[Clock, Exception, String] =
    ZSTM.atomically {
      for {
        _  <- STM.unit
        (aCounter, aMap) = (accounts.counter, accounts.zmap)
        (bCounter, bMap) = (blocks.counter, blocks.zmap)
        (tCounter, tMap) = (transactions.counter, transactions.zmap)

        fee = roundFloat(txnFeePct * amount)
        iTxn  <- tCounter.updateAndGet(_ + 1).map(i =>
                  Transaction(i + initialTxn, amount, fee, payload, p, r)
                 )
        _     <- validate(iTxn, aMap) *> process(iTxn, aMap) *> tMap.put(iTxn.id, iTxn)

        openBlockId  <- bCounter.get
        _            <- bMap.merge(openBlockId, emptyBlock) { (b1, _) =>
                         b1.copy(transactions = b1.transactions :+ iTxn.id)
                        }

        txnReached   <- bMap.get(openBlockId).map(_.get.transactions.size == txnPerBlock)
        _            <- snapshot(aMap).flatMap(closeBlock(bMap, bCounter, _))
                        .when(txnReached)

      } yield iTxn.id.toString
    } retry Schedule.duration(3.seconds)


  private def validate(txn: Transaction, aMap: TMap[Address, Account]):
  STM[InvalidTransaction, Unit] =
    for {
      _  <- aMap.contains(txn.payer).zipWith(aMap.contains(txn.receiver))(_ && _)
              .filterOrFail(_.self)(InvalidTransaction("Invalid addresses"))
      _  <- aMap.get(txn.payer).map(acc => acc.get.balance >= txn.amount + txn.fee)
              .filterOrFail(_.self)(InvalidTransaction("Not enough balance"))
    } yield ()


  private def process(txn: Transaction, aMap: TMap[Address, Account]): STM[Nothing, Unit] =
    for {
      _  <- aMap.merge(txn.payer, emptyAccount(txn.amount + txn.fee)) { (acc1, acc2) =>
              acc1.copy(balance = acc1.balance - acc2.balance)
            } *>
            aMap.merge(txn.receiver, emptyAccount(txn.amount)) { (acc1, acc2) =>
              acc1.copy(balance = acc1.balance + acc2.balance)
            } *>
            aMap.merge(masterAcc, emptyAccount(txn.fee)) { (acc1, acc2) =>
              acc1.copy(balance = acc1.balance + acc2.balance)
            }
    } yield()


  private def closeBlock(bMap: TMap[Counter, Block], bCounter: TRef[Int], snapshot: List[(Name, Float)]):
  STM[Nothing, Unit] = {
    for {
      latestBlock    <- bCounter.get
      prevBlockHash  <- bMap.getOrElse(latestBlock - 1, emptyBlock).map(_.hash)
      _              <- bMap.merge(latestBlock, emptyBlock) { (b1, _) =>
                          b1.copy(hash = blockSha256(b1, prevBlockHash), balances = snapshot)
                        }
      _              <- bCounter.updateAndGet(_ + 1).flatMap(c =>  bMap.put(c, emptyBlock))
    } yield ()
  }

  def displayBlocks: URIO[Console, Unit] = for {
    c  <- blocks.counter.get.commit
    _  <- putStrLn("\n\n====== BLOCKS ======\n")
    _  <- ZIO.loop_(c)(_ >= 1, _ -1) { c =>
           blocks.zmap.getOrElse(c, emptyBlock).commit
           .map(blockToString).tap(s => putStrLn(s"Block ${c}:\t" + s))
          }
  } yield ()
}


/**
 * Ledger companion object.
 */
object Ledger {
  type Address = String
  type Name = String
  type Counter = Int
  type Id = Int

  case class ZMap[K, V](counter: TRef[Int], zmap: TMap[K, V])

  def create(config: Config): UIO[Ledger] = {
    val masterAccount = Account("master", generateUUID(config.seed), config.currency)

    for {
      c0  <- TRef.make(0).commit
      c1  <- TRef.make(1).commit
      c2  <- TRef.make(1).commit
      accMap  <- TMap.make((masterAccount.nickName, masterAccount)).commit
      blkMap  <- TMap.make((1, emptyBlock)).commit
      txnMap  <- TMap.empty[Id, Transaction].commit
      ledger = Ledger(
                ZMap(c1, accMap), ZMap(c2, blkMap), ZMap(c0, txnMap),
                config.txnPerBlock, config.txnFeePct, config.initialTxnNum,
                masterAccount.nickName, config.seed
               )
    } yield ledger
  }

  def generateUUID(s: String): String =
    java.util.UUID.nameUUIDFromBytes(s.getBytes()).toString

  private def blockSha256(b: Block, prev: String): String =
    sha256Hash(prev + b.transactions.map(_.hashCode()).sum + b.balances.map(_.hashCode()).sum)

  private def sha256Hash(text: String): String = {
    val sha256 = MessageDigest
      .getInstance("SHA-256")
      .digest(text.getBytes("UTF-8"))

    String.format("%064x", new java.math.BigInteger(1, sha256))
  }

  private def roundFloat(f: Float): Float = (math.rint(f * 100) / 100).toFloat

  private def snapshot(aMap: TMap[Name, Account]): STM[Nothing, List[(Name, Float)]] =
    aMap.toList.map(m => m.map(t => t._1 -> t._2.balance))

  private[ledger] def blockToString(b: Block): String =
    b.productIterator.map("\n\t\t" + _.toString).toList.toString
      .replaceFirst("List", "Block")
      .replaceFirst("List", "Transactions")
      .replaceFirst("List", "Balances")

  private[ledger] def emptyAccount(f: Float): Account = Account("", "", f)
  private[ledger] def emptyBlock: Block = Block("", List.empty, List.empty)
}


