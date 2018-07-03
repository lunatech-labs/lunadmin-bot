package api

import reactivemongo.play.json._
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.api.{ DB, MongoConnection, MongoDriver }

trait ReactiveMongoApi {
  def driver: MongoDriver
  def connection: MongoConnection
  def db: DB
}
