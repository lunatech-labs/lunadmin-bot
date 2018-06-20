package models

import java.text.SimpleDateFormat
import java.util.Date


abstract class Task(
  id : Int,
  description : String,
  startDate : Date,
  endDate : Date,
  status : String,
  category : String,
  alert : Option[List[Date]]
){

  def getNameById(userList : List[User],groupList : List[UserGroup]) : String

  def getDate(date : Date) : String= {
    val dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    val stringToReturn = dateFormat.format(date)
    stringToReturn
  }

  def getAlert() : String = {
    var stringToReturn = ""
    if(alert.isDefined) {
      alert.get.foreach(d => stringToReturn = stringToReturn + getDate(d))
    }else{
      stringToReturn = stringToReturn + "No alert set up"
    }
    stringToReturn
  }

  def getId() = id
  def getDescription() = description
  def getStartDate() = getDate(startDate)
  def getEndDate() = getDate(endDate)
  def getStatus() = status
  def getCategory() = category
}

