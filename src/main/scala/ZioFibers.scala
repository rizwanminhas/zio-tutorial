import zio.clock._
import zio.{Exit, ExitCode, UIO, URIO, ZIO}
import zio.duration._

/**
 * fiber = scheduled computation
 * computation = value + an effect in the world (printing, network call, io operation, etc)
 */
object ZioFibers extends zio.App {

  val showerTime = ZIO.succeed("taking shower")
  val boilingWater = ZIO.succeed("boiling some water")
  val preparingTea = ZIO.succeed("preparing my tea")

  def printThread = s"[${Thread.currentThread().getName}]"

  def concurrentShowerAndBoilingWater() = for {
    _ <- showerTime.debug(printThread).fork
    _ <- boilingWater.debug(printThread)
    _ <- preparingTea.debug(printThread)
  } yield ()

  /*
    A "fork join" implementation in functional style.
    shower and boiling water will execute on separate threads at the same time.
    Their result will be combined in a separate thread
    preparingTea will be executed after that.
   */
  def concurrentRoutine() = for {
    showerFiber <- showerTime.debug(printThread).fork
    boilingWaterFiber <- boilingWater.debug(printThread).fork
    zippedFiber = showerFiber.zip(boilingWaterFiber)
    result <- zippedFiber.join.debug(printThread) // join means wait for both zipped fibers to complete and only then continue
    _ <- ZIO.succeed(s"$result DONE").debug(printThread) *> preparingTea.debug(printThread) // *> is a sequence operator or an "and then"
  } yield ()

  def synchronousRoutine() = for {
    _ <- showerTime.debug(printThread)
    _ <- boilingWater.debug(printThread)
    _ <- preparingTea.debug(printThread)
  } yield ()

  val callFromFriend: UIO[String] = ZIO.succeed("Call from a friend...")
  val boilingWaterWithTime: ZIO[Clock, Nothing, String] = boilingWater.debug(printThread) *> ZIO.sleep(5.seconds) *> ZIO.succeed("Boiled water ready.")

  def concurrentRoutineWithFriendCall(): ZIO[Clock, Nothing, Unit] = for {
    _ <- showerTime.debug(printThread)
    boilingFiber <- boilingWaterWithTime.fork
    _ <- callFromFriend.debug(printThread).fork *> ZIO.sleep(2.seconds) *> boilingFiber.interrupt.debug(printThread)
    _ <- ZIO.succeed(s"going out with the friend").debug(printThread)
  } yield ()

  val preparingTeaWithTeam = preparingTea.debug(printThread) *> ZIO.sleep(3.seconds) *> ZIO.succeed("Tea is ready.")

  def concurrentRoutineWithTeaAtHome() = for {
    _ <- showerTime.debug(printThread)
    _ <- boilingWater.debug(printThread)
    teaFiber <- preparingTeaWithTeam.debug(printThread).fork.uninterruptible
    result <- callFromFriend.debug(printThread).fork *> teaFiber.interrupt.debug(printThread)
    _ <- result match {
      case Exit.Success(value) => ZIO.succeed(s"Sorry, making breakfast at home. Because [$value]").debug(printThread)
      case _ => ZIO.succeed("Going out with friend").debug(printThread)
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    //synchronousRoutine().exitCode
    //concurrentShowerAndBoilingWater().exitCode
    //concurrentRoutine.exitCode
    //concurrentRoutineWithFriendCall().exitCode
    concurrentRoutineWithTeaAtHome().exitCode
  }
}
