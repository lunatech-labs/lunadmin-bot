package models

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

case class UserDescription (
                             _id : String = BSONObjectID.generate().stringify,
                             firstName : String,
                             lastName : String,
                             mail : String,
                           )

object UserDescription {
  implicit val fmt = Json.format[UserDescription]
}