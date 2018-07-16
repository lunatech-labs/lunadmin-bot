package models

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID



case class TaskCategory (
                          _id : String = BSONObjectID.generate().stringify ,
                          name : String,
                          idOfCategoryParent : Option[String] = None,
                          isHeader : Boolean
)

object TaskCategory {
  implicit val fmt = Json.format[TaskCategory]
}
