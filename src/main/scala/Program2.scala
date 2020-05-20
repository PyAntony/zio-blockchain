import Program.{readDslFile, sendResponse}
import ledger.command.Command
import ledger.core.Components.Config
import ledger.core.Ledger


import zio._
import zio.clock._
import zio.console._
import zio.stm.{STM, TRef}


object Program2 extends App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {

    val program: ZIO[Console with Clock, Exception, Int] = for {
      ref    <- TRef.make(0).commit
      state  <- UIO( State(ref) )
      workers  = List.fill(4)(state.update())

      fiber       <- ZIO.forkAll(workers)
      _           <- fiber.join
      finalValue  <- state.ref.get.commit

      // finalValue == 0 ! I expected 4
      _           <- putStrLn(finalValue.toString)
    } yield 0

    program orElse ZIO.succeed(1)
  }

  case class State(ref: TRef[Int]) {
    def update(): UIO[Unit] = STM.atomically {
      for {
        n <- ref.updateAndGet(_ + 1)
      } yield ()
    }
  }

}


