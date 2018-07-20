package models

import java.time.LocalDate

case class UserAddForm(
                 mail : String,
                 password : String,
                 firstName : String,
                 lastName : String,
                 birthDate : LocalDate,
                 groupName : Option[List[String]] = None,
                 status : Option[String] = Some("user"),
                 hireDate : LocalDate,
                 phone : Option[String] = None,
                 isActive : Boolean = true,
                 timeZone : String = "Europe/Paris"
               )