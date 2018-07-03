package models

import play.api.libs.json.Json

case class AdministrativPaper (
      id : Int,
      name : String,
)

object AdministrativPaper {
  implicit val fmt = Json.format[AdministrativPaper]
}
