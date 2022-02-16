package gitAnalyzer

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.alpakka.mongodb.scaladsl.MongoSource
import akka.stream.scaladsl.Sink
import spray.json.DefaultJsonProtocol
import gitAnalyzer.dbDTO.{Entity}
import akka.http.scaladsl.server.Directives._
import scala.io.StdIn
import scala.util.{Failure, Success}

final case class EntitiesList(entities: Seq[Entity])

trait JSONSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val messageFormat = jsonFormat4(Entity)
  implicit val entitiesListFormat = jsonFormat1(EntitiesList)
}

object GitAnalyzer extends App with JSONSupport {
  implicit val system = ActorSystem(MainActor(), "rootSystem")
  implicit val executionContext = system.executionContext

  val rootRoute = concat (
    path("entities") {
      parameters(Symbol("repo").as[String]) { (repo) =>
        val entitiesSource = MongoSource(db.entryCollection.find().limit(100))
          .filter(entity => entity.repo == repo)
          .runWith(Sink.seq)

        onComplete(entitiesSource) {
          case Success(entities) => complete(EntitiesList(entities))
          case Failure(e) => {
            println(s"error $e.getMessage")
            complete(EntitiesList(Seq()))
          }
        }
      }
    },
  )

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(rootRoute)

  println("Server now online at http://localhost:8080")

  StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
