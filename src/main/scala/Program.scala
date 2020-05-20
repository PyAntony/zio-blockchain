
import zio._
import zio.clock._
import zio.console._
import zio.duration._

import scala.io.Source
import java.io.IOException

import ledger.core._
import ledger.core.Components._
import ledger.command.Command
import ledger.command.Process._


object Program extends App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {

    val ledgerConfig: Config = Config("ledger-1", "seed-0", 1000, 4, Float.MaxValue, 0.01F)
    val dslFileName: String = "commands.txt"

    val program: ZIO[Console with Clock, Exception, Int] = for {
      q       <- Queue.unbounded[String]
      lines   <- readDslFile(dslFileName)
      _       <- q.offerAll(lines).fork
      ledger  <- Ledger.create(ledgerConfig)

      effect   = q.take.flatMap(Command.get(_).process(ledger))
                  .flatMap(sendResponse).forever
      workers  = List.fill(4)(effect)

      fiber   <- ZIO.forkAll(workers)
      _       <- q.size.doUntil(_ <= 0) *> fiber.join.timeout(5.seconds)
      _       <- ledger.displayBlocks
    } yield 0

    program orElse ZIO.succeed(1)
  }

  def readDslFile(file: String): ZIO[Any, IOException, List[String]] = {
    val effect = Task(Source.fromResource(file)).bracket(b => ZIO.effectTotal(b.close)) {
      buffer => Task(parseLines(buffer.getLines))
    }
    effect.refineToOrDie[IOException]
  }

  def parseLines(it: Iterator[String]): List[String] = {
    it.filterNot {
      l => l.isEmpty || List("#", " ").exists(l.startsWith)
    }.map(_.toLowerCase).toList
  }

  def sendResponse(r: Response): ZIO[Console, Nothing, Unit] =
    putStrLn(r.command + " ===>\n\t" + r.message)
}

