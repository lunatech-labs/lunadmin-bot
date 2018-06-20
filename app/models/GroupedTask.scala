package models

import java.util.Date
import java.text.SimpleDateFormat
import play.Logger

case class GroupedTask (
  id : Int,
  description : String,
  startDate : Date,
  endDate : Date,
  status : String,
  group : List[UserGroup],
  category : String,
  alert : Option[List[Date]] = None
) extends Task(id,description,startDate,endDate,status,category,alert){

  override def toString: String = {
    val dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    s"Grouped Task - $id - $description - ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)} - $status - ${group.toString} - $category - ${alert.toString}"
  }

  override def getNameById(userList : List[User], groupList : List[UserGroup]): String = {

    val listOfGroupIn = groupList.filter(userG => group.contains(userG))
    var listOfPeopleIn = List[User]()
    userList.foreach(p => if(p.groupId.isDefined){
      val listOfIntersection = p.groupId.get.intersect(listOfGroupIn.map(userG => userG.id))
      if(listOfIntersection.nonEmpty)
          listOfPeopleIn = listOfPeopleIn :+ p
    })


    if(listOfPeopleIn.nonEmpty) {
      var stringToReturn = ""
    for(g <- listOfGroupIn){
        stringToReturn = stringToReturn + s"${g.name},"
    }
      stringToReturn = stringToReturn.dropRight(1)
      stringToReturn
    }else{
      "Nobody is affected to this task"
    }
  }
}
