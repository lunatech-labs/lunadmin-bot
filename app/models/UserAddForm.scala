package models

import java.time.LocalDate

case class UserAddForm(
                 mail : String,
                 password : String,
                 firstName : String,
                 lastName : String,
                 birthDate : Option[LocalDate],
                 groupName : Option[List[String]] = None,
                 status : Option[String] = Some("user"),
                 hireDate : Option[LocalDate],
                 picture : Option[String] = None,
                 phone : Option[String] = None,
                 cloudLinks : List[String],
                 isActive : Boolean = true,
                 timeZone : String = "Europe/Paris"
               )