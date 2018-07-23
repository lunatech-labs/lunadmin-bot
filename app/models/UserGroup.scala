package models

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

case class UserGroup (
                       _id : String = BSONObjectID.generate().stringify,
                       name : String
)
object UserGroup {
  implicit val fmt = Json.format[UserGroup]
}
