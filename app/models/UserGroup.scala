package models

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class UserGroup (
                       _id : String = BSONObjectID.generate().stringify,
                       name : String
)
object UserGroup {
  implicit val fmt = Json.format[UserGroup]
}
