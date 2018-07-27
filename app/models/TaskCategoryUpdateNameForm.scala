package models

import play.api.libs.json.Json

case class TaskCategoryUpdateNameForm(
                                  oldName : String,
                                  newName : String
                                  )

object TaskCategoryUpdateNameForm{
  implicit val fmt = Json.format[TaskCategoryUpdateNameForm]
}