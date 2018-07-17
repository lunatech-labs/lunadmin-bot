package models

import play.api.libs.json.Json

case class mailTemplate (
                        mail : String,
                        _id : String
                        )

object mailTemplate {
  implicit val fmt = Json.format[mailTemplate]
}
