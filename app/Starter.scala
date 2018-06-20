package controllers

import java.util.Calendar

import com.google.inject.Singleton
import controllers.AssetsFinder
import javax.inject.Inject
import models._
import play.api.mvc.{AbstractController, ControllerComponents}


@Singleton
class Starter @Inject()(cc: ControllerComponents) (implicit assetsFinder: AssetsFinder)
  extends AbstractController(cc) {

val controllerComponent = cc
var listOfTask = List[Task]()
var listOfUsers = List[User]()
var listOfGroup = List[UserGroup]()
  main()

  def main(): Unit ={
    listOfGroup = listOfGroup :+ UserGroup(0,"Dev")
    listOfGroup = listOfGroup :+ UserGroup(1,"Admin")
    listOfGroup = listOfGroup :+ UserGroup(2,"Consultant")
    listOfGroup = listOfGroup :+ UserGroup(3,"Dieu")

    listOfTask = listOfTask :+ SinglePersonTask(0,"some description",Calendar.getInstance().getTime,Calendar.getInstance().getTime,"petit nouveau",0,"anniv")
    listOfTask = listOfTask :+ SinglePersonTask(1,"some description",Calendar.getInstance().getTime,Calendar.getInstance().getTime,"petit nouveau",1,"anniv")
    listOfTask = listOfTask :+ SinglePersonTask(2,"some description",Calendar.getInstance().getTime,Calendar.getInstance().getTime,"petit nouveau",2,"anniv")
    listOfTask = listOfTask :+ SinglePersonTask(3,"some description",Calendar.getInstance().getTime,Calendar.getInstance().getTime,"petit nouveau",3,"anniv")

    listOfTask = listOfTask :+ GroupedTask(4,"some description",Calendar.getInstance().getTime,Calendar.getInstance().getTime,"petit nouveau",listOfGroup.filter(p => p.id == 0),"anniv")
    listOfTask = listOfTask :+ GroupedTask(5,"some description",Calendar.getInstance().getTime,Calendar.getInstance().getTime,"petit nouveau",listOfGroup.filter(p => p.id == 2),"anniv")
    listOfTask = listOfTask :+ GroupedTask(6,"some description",Calendar.getInstance().getTime,Calendar.getInstance().getTime,"petit nouveau",listOfGroup.filter(p => p.id == 3),"anniv")
    listOfTask = listOfTask :+ GroupedTask(7,"some description",Calendar.getInstance().getTime,Calendar.getInstance().getTime,"petit nouveau",listOfGroup,"c la fettteeeee")


    listOfUsers = listOfUsers :+ User("alala@hotmail.com","dedewded","douglas","mcpetitbonhomme",groupId = Some(List(0,2)),id = Some(0))
    listOfUsers = listOfUsers :+ User("samsamsam@hotmail.com","dedewded","samanta","mcpetitbonfemme",groupId = Some(List(1,3)), id = Some(1))
    listOfUsers = listOfUsers :+ User("partout@partout.partout","dedewded","partout","mcpartout",groupId = Some(List(0,3)),id = Some(2))
    listOfUsers = listOfUsers :+ User("alibabalemacaron@bouafle.fr","dedewded","john","doe", id = Some(3))
  }

}



