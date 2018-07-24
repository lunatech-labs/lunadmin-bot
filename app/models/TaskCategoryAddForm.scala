package models

import play.api.libs.json.Json

case class TaskCategoryAddForm (
                               name : String,
                               isHeader : Boolean,
                               idOfParent : Option[String]
                               )

object TaskCategoryAddForm{
  implicit val fmt = Json.format[TaskCategoryAddForm]
}
