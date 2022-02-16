package gitAnalyzer

import com.mongodb.reactivestreams.client.MongoClients
import gitAnalyzer.dbDTO._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._

object db {
  private val client = MongoClients.create("mongodb://localhost:27017")
  private val db = client.getDatabase("Entries")

  private val codecRegistry = fromRegistries(fromProviders(classOf[Entity]), DEFAULT_CODEC_REGISTRY)

  val entryCollection = db
    .getCollection("entries", classOf[Entity])
    .withCodecRegistry(codecRegistry)
}
