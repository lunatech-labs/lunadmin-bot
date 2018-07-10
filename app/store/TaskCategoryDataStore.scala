package store

import javax.inject.Inject
import models._
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Json, _}

class TaskCategoryDataStore @Inject()(val reactiveMongoApi: ReactiveMongoApi ,conf :Configuration)(implicit ec: ExecutionContext) {
  private val taskCategoryCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("taskCategory"))
  import reactivemongo.play.json.ImplicitBSONHandlers._
  val listOfBaseTaskCategory = conf.underlying.getStringList("taskCategory.default.tags")

//  // function to insert the Base User Group if they does not exist in the MongoDB
//  def initializeUserGroupData() = {
//    val listOfExistingUserGroup = findAllUserGroupForCheck().map(f => f.map(element => element.name))
//    listOfExistingUserGroup.map{listExisting =>
//      listOfBaseUserGroup.forEach{element =>
//        if(!listExisting.contains(element)){
//          insertUserGroup(UserGroup(name = element))
//        }
//      }
//    }
//  }

  def insertTaskCategory(taskCategory : TaskCategory) = {
    val javaDoc = TaskCategory.fmt.writes(taskCategory)
    taskCategoryCollection.map(c => c.insert(javaDoc))
  }

  def deleteTaskCategory(taskCategoryName : String) = {
    val removeQuery = Json.obj("name" -> taskCategoryName)
    taskCategoryCollection.map(c => c.remove(removeQuery))
  }

  def findAllTaskCategory() : Future[List[TaskCategory]] = {
    val query = BSONDocument()
    taskCategoryCollection.flatMap(
      _.find(query)
        .cursor[TaskCategory]()
        .collect[List](-1, Cursor.FailOnError[List[TaskCategory]]())
    )
  }

  private def findAllTaskCategoryForCheck() : Future[List[TaskCategory]] = {
    val query = BSONDocument()
    taskCategoryCollection.flatMap(
      _.find(query)
        .cursor[TaskCategory]()
        .collect[List](-1, Cursor.FailOnError[List[TaskCategory]]()))
  }
}