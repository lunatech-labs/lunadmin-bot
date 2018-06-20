package models

import java.text.SimpleDateFormat
import java.util.Date


case class SinglePersonTask (
   id : Int,
   description : String,
   startDate : Date,
   endDate : Date,
   status : String,
   employeeId : Int,
   category : String,
   alert : Option[List[Date]] = None
)extends Task(id,description,startDate,endDate,status,category,alert){

  override def toString: String = {
    val dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    s"SimplePerson Task - $id - $description - ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)} - $status - ${employeeId.toString} - $category - ${alert.toString}"
  }

  override def getNameById(userList : List[User], groupList : List[UserGroup]): String = {
    val employeeConcerned = userList.find(p => p.id.get == employeeId)
    var stringToReturn = ""
    if(employeeConcerned.isDefined) {
      stringToReturn = s"${employeeConcerned.get.firstName} ${employeeConcerned.get.lastName}"
    }else{
      stringToReturn = "Error On Creation Of Task"
    }
    stringToReturn
  }


}
