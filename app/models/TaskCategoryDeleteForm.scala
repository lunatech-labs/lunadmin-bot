package models

import play.api.libs.json.Json

case class TaskCategoryDeleteForm (
                                  name : String
                                  )
object TaskCategoryDeleteForm{
  implicit val fmt = Json.format[TaskCategoryDeleteForm]
}