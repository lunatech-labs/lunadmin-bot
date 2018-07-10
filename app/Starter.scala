package controllers

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
  }


  def removeUserGroup(userGroupName : String) = {
    userDataStore.removeUserGroupFromUser(userGroupName)
    userGroupDataStore.deleteUserGroup(userGroupName)
    taskDataStore.removeTaskCategoryFromTask(userGroupName)
  }
}



