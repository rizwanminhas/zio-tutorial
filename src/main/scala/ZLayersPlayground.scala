import ZLayersPlayground.UserDb.UserDbEnv
import ZLayersPlayground.UserEmailer.UserEmailerEnv
import zio.{Has, Task, ZIO, ZLayer}
import zio.console.{getStrLn, putStrLn}

object ZLayersPlayground extends zio.App {

  // ZIO[R, E, V] = "effects"
  // R = input or environment
  // E = Error
  // V = Result our output
  // R => Either[E, A]


  // ...
  val meaningOfLife = ZIO.succeed(42)
  val aFailure = ZIO.fail("wrong...")
  println(meaningOfLife)

  // ...
  val greeting = for {
      _ <- putStrLn("enter name:")
      name <- getStrLn
      _ <- putStrLn(s"Hello, $name!")
  } yield ()

  case class User(name: String, email: String)

  object UserEmailer {
    type UserEmailerEnv = Has[UserEmailer.Service]
    // Service definition
    trait Service {
      // Task[Unit] = ZIO[Any, Throwable, Unit]
      def notify(user: User, message: String): Task[Unit]
    }
    // Service implementation
    val live: ZLayer[Any, Nothing, UserEmailerEnv] = ZLayer.succeed(new Service {
      override def notify(user: User, message: String): Task[Unit] = Task {
        println(s"Sending the message [$message] to user [${user.email}]")
      }
    })
    // front-facing API
    def notify(user: User, message: String): ZIO[UserEmailerEnv, Throwable, Unit] =
    ZIO.accessM(hasService => hasService.get.notify(user, message))
  }

  object UserDb {
    type UserDbEnv = Has[UserDb.Service]
    trait Service {
      def insert(user: User): Task[Unit]
    }
    val live: ZLayer[Any, Nothing, UserDbEnv] = ZLayer.succeed((user: User) => Task {
      println(s"Insert $user into users table")
    })

    def insert(user: User): ZIO[UserDbEnv, Throwable, Unit] =
      ZIO.accessM(_.get.insert(user))
  }

  override def run(args: List[String]) = {

    // horizontal composition
    // ZLayer[Int1, Err1, Out1] ++ ZLayer[Int2, Err2, Out2] => ZLayer[Int1 with Int2, super(Err1, Err2), Out1 with Out2]

    val userBackendLayer: ZLayer[Any, Nothing, UserEmailerEnv with UserDbEnv] = UserEmailer.live ++ UserDb.live

    // vertical composition
    object UserSubscription {
      type UserSubscriptionEnv = Has[UserSubscription.Service]
      class Service(userEmailer: UserEmailer.Service, userDb: UserDb.Service) {
        def subscribe(user: User): Task[User] = {
          for {
            _ <- userDb.insert(user)
            _ <- userEmailer.notify(user, s"Welcome, ${user.name}! Here's my first news letter")
          } yield user
        }
      }
      val live: ZLayer[UserEmailerEnv with UserDbEnv, Nothing, UserSubscriptionEnv] = ZLayer.fromServices[UserEmailer.Service, UserDb.Service, UserSubscription.Service] {
        (userEmailer, userDb) => new Service(userEmailer, userDb)
      }

      // front-facing API
      def subscribe(user: User): ZIO[UserSubscriptionEnv, Throwable, User] = ZIO.accessM(_.get.subscribe(user))
    }

    import UserSubscription.UserSubscriptionEnv
    val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscriptionEnv] = userBackendLayer >>> UserSubscription.live


    // 1
    // greeting.exitCode
    val user = User("rizwan", "rizwan@test.com")
    val message = "Welcome to learning ZIO."
    // 2
//    UserEmailer.notify(user, message) // the kind of effect
//      .provideLayer(UserEmailer.live) // provide the input for that effect i.e. Dependency Injection
//      .exitCode // run the effect

    // 3
//    UserEmailer.notify(user, message) // the kind of effect
//      .provideLayer(userBackendLayer) // provide the input for that effect i.e. Dependency Injection
//      .exitCode // run the effect

    // 4
    UserSubscription.subscribe(user).provideLayer(userSubscriptionLayer).exitCode
  }

}
