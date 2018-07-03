package models

import play.api.Logger
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat



case class TaskCategory (
                          _id : String = BSONObjectID.generate().stringify ,
                          nameOfCategory : String,
                          idOfCategoryParent : Option[String] = None
)

object TaskCategory {
  implicit val fmt = Json.format[TaskCategory]
}
