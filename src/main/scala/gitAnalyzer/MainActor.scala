package gitAnalyzer

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, headers}
import akka.stream.scaladsl.{Source}
import akka.http.scaladsl.unmarshalling.Unmarshal

import scala.concurrent.{Await}
import scala.concurrent.duration._
import akka.stream.alpakka.mongodb.scaladsl.{MongoSink}

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}
import scala.util.parsing.json.JSON

object MainActor {
  final case class Message(text: String)

  def apply(): Behavior[Message] = Behaviors.setup { (context) =>
    implicit val system = context.system
    implicit val executionContext = system.executionContext

    val authorization = headers.RawHeader("Authorization", "token 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")

    val source = Source
      .tick(
        10.seconds,
        1.minute,
        (
          HttpRequest(
            uri = "/search/code?q=os.getcwd&sort=indexed",
            method = HttpMethods.GET,
            headers = List(authorization),
          ),
          1
        )
      )

    val connectionFlow = Http().newHostConnectionPoolHttps[Int](
      host = "api.github.com",
      port = 443,
    )

    source
      .via(connectionFlow)
      .map(res => {
        val r = res._1;

        r match {
          case Success(r) =>
            if (r.status.intValue() == 200) {
              println(s"Success: ${r.status}")

              val entityFuture = r.entity
                .toStrict(10.seconds)
                .flatMap(item => {
                  Unmarshal(item).to[String]
                });

              val maxWaitTime: FiniteDuration = Duration(15, TimeUnit.SECONDS)
              val result = Await.result(entityFuture, maxWaitTime);

              val entities = JSON.parseFull(result) match {
                case Some(json) =>
                  val map = json.asInstanceOf[Map[String, Any]]
                  val list = map("items").asInstanceOf[List[Map[String, Any]]]
                  val entities = list.map(map2 => {
                    val sha = map2("sha").asInstanceOf[String]
                    val file = map2("name").asInstanceOf[String]
                    val path = map2("path").asInstanceOf[String]
                    val repo = map2("repository").asInstanceOf[Map[String, Any]]("full_name").asInstanceOf[String]

                    dbDTO.Entity(sha, file, path, repo)
                  })

                  println(entities)
                  entities
                case None => throw new Exception("Error parsing JSON")
              }

              entities
            } else {
              throw new Exception(s"Failure: ${r.status}")
            }
          case Failure(e) =>
            throw e
        }
      })
      .runWith(MongoSink.insertMany(db.entryCollection))

    Behaviors.same
  }
}
