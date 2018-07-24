package models

import play.api.libs.json.Json

case class TaskCategoryUpdateForm (
                                  oldName : String,
                                  newName : String
                                  )

object TaskCategoryUpdateForm{
  implicit val fmt = Json.format[TaskCategoryUpdateForm]
}