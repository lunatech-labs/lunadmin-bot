package models

import play.api.libs.json.Json

case class UserGroupAddForm (
                            name : String
                            )

object UserGroupAddForm {
  implicit val fmt = Json.format[UserGroupAddForm]
}
