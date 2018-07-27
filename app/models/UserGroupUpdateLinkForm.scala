package models

import play.api.libs.json.Json

case class UserGroupUpdateLinkForm (
                               id : String,
                               link : Option[String]
                            )

object UserGroupUpdateLinkForm {
  implicit val fmt = Json.format[UserGroupUpdateLinkForm]
}