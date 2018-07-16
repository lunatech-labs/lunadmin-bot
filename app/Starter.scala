package controllers

import java.time.LocalDate

import com.google.inject.Singleton
import javax.inject.Inject
import models._
import play.Logger
import play.api.Configuration
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.libs.json._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import store.{TaskCategoryDataStore, TaskDataStore, UserDataStore, UserGroupDataStore}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Starter @Inject()(tCDS : TaskCategoryDataStore ,uGDS : UserGroupDataStore, uDS : UserDataStore, tDS: TaskDataStore, configuration : Configuration, cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi)(implicit assetsFinder: AssetsFinder, ec: ExecutionContext)
  extends AbstractController(cc) with MongoController with ReactiveMongoComponents {

  val controllerComponent = cc
  val userGroupDataStore = uGDS
  val userDataStore = uDS
  val taskDataStore = tDS
  val taskCategoryDataStore = tCDS

  val listOfTaskStatus = configuration.underlying.getStringList("taskStatus.default.tags").asScala.toList

  main()

  def main(): Unit ={
    userGroupDataStore.initializeUserGroupData()
    taskCategoryDataStore.initializeListOfBaseTaskCategory()
    taskCategoryDataStore.initializeTaskCategoryData()

    userDataStore.addUser(User(mail = "LunAdmin@gmail.com",password = "admin",firstName = "Admin",lastName = "Lunatech",status = Some("Admin"),picture = Some("\uFEFFhttps://upload.wikimedia.org/wikipedia/en/thumb/7/7d/Lenna_%28test_image%29.png/220px-Lenna_%28test_image%29.png")))

  }


  def removeUserGroup(userGroupName : String) = {
    userDataStore.removeUserGroupFromUser(userGroupName)
    userGroupDataStore.deleteUserGroup(userGroupName)
    taskDataStore.removeTaskCategoryFromTask(userGroupName)
  }
}



