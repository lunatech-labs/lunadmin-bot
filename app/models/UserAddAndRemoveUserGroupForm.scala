package models

import play.api.libs.json.Json

case class UserAddAndRemoveUserGroupForm(
                                 id : String
                                 )

object UserAddAndRemoveUserGroupForm{
  implicit val fmt = Json.format[UserAddAndRemoveUserGroupForm]
}
