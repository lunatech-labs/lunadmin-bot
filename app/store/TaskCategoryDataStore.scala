package store

import javax.inject.{Inject, Singleton}
import models._
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Json, _}
import scala.collection.JavaConverters._

@Singleton
class TaskCategoryDataStore @Inject()(val reactiveMongoApi: ReactiveMongoApi ,conf :Configuration)(implicit ec: ExecutionContext) {
  private val taskCategoryCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("taskCategory"))
  import reactivemongo.play.json.ImplicitBSONHandlers._
  private val listOfBaseTaskCategoryHeader = conf.underlying.getStringList("taskCategory.default.tags")
  var listOfBaseTaskCategoryWithHeader : List[(String,List[String])]= List()

  def initializeListOfBaseTaskCategory() : Unit = {
    listOfBaseTaskCategoryHeader.forEach{h =>
      listOfBaseTaskCategoryWithHeader = listOfBaseTaskCategoryWithHeader :+ (h,conf.underlying.getStringList("taskCategory.default."+h).asScala.toList)
    }
  }

  // function to insert the Base Task Category if they does not exist in the MongoDB
  def initializeTaskCategoryData() : Unit = {
    val listOfExistingUserGroup = findAllTaskCategory().map(f => f.map(element => element.name))
    listOfExistingUserGroup.map{listExisting =>
      listOfBaseTaskCategoryWithHeader.foreach{ e =>
        // Check if the category header is already inserted
        if(!listExisting.contains(e._1)){
         insertTaskCategory(TaskCategory(name = e._1,isHeader = true))
        }
        // Check if every task category corresponding to the header is inserted
        e._2.foreach{ f =>
          if(!listExisting.contains(f)){
            findTaskCategoryByName(e._1).map { p =>
              insertTaskCategory(TaskCategory(name = f, idOfCategoryParent = Some(p._id),isHeader = false))
            }
          }
        }
      }
    }
  }

  def findTaskCategoryById(idOfTaskCategory : String) : Future[TaskCategory] = {
    val query = Json.obj("_id" -> idOfTaskCategory)
    taskCategoryCollection.flatMap(
      _.find(query)
        .cursor[TaskCategory]()
        .head
    )
  }

  def findTaskCategoryByName(nameOfTasKCategory : String) : Future[TaskCategory] = {
    val query = Json.obj("name" -> nameOfTasKCategory)
    taskCategoryCollection.flatMap(
      _.find(query)
        .cursor[TaskCategory]()
        .head
    )
  }

  def insertTaskCategory(taskCategory : TaskCategory) : Unit = {
    val javaDoc = TaskCategory.fmt.writes(taskCategory)
    taskCategoryCollection.map(c => c.insert(javaDoc))
  }

  def deleteTaskCategory(taskCategoryId : String) : Unit = {
    val removeQuery = Json.obj("_id" -> taskCategoryId)
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

  private def findAllTaskHeader() : Future[List[TaskCategory]] = {
    val query = BSONDocument("isHeader" -> true)
    taskCategoryCollection.flatMap(
      _.find(query)
        .cursor[TaskCategory]()
        .collect[List](-1, Cursor.FailOnError[List[TaskCategory]]())
    )
  }

  def findHeaderTaskChildren(idOfHeader : String) : Future[List[TaskCategory]] = {
    val query = BSONDocument("idOfCategoryParent" -> idOfHeader)
    taskCategoryCollection.flatMap(
      _.find(query)
        .cursor[TaskCategory]()
        .collect[List](-1, Cursor.FailOnError[List[TaskCategory]]())
    )
  }

  def updateTaskCategory(idOfTC : String, newTC : TaskCategory) : Unit = {
    val selectQuery = Json.obj("_id" -> idOfTC)
    val updateQuery = Json.obj("name" -> newTC.name,
                               "idOfCategoryParent" -> newTC.idOfCategoryParent,
                               "isHeader" -> newTC.isHeader)
    taskCategoryCollection.map(c => c.update(selectQuery,updateQuery))
  }
}