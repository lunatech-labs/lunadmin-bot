package models

import play.api.libs.json.Json

case class TaskCategoryUpdateLinkForm(
                                       name : String,
                                       newLink : Option[String]
                                     )

object TaskCategoryUpdateLinkForm{
  implicit val fmt = Json.format[TaskCategoryUpdateLinkForm]
}