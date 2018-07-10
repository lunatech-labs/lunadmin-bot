package store

import java.time.ZonedDateTime
import java.util.Date

import javax.inject.Inject
import models._
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}


class TaskDataStore @Inject()(val reactiveMongoApi: ReactiveMongoApi ,conf :Configuration)(implicit ec: ExecutionContext) {

  private val taskCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("task"))
  import reactivemongo.play.json.ImplicitBSONHandlers._

  def findAllTaskDescription(page : Int, pageSize : Int) : Future[List[TaskDescription]] = {
    val query = BSONDocument()
    taskCollection.flatMap(
      _.find(query)
      .options(QueryOpts(skipN = page * pageSize, pageSize))
      .cursor[TaskDescription]()
      .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findTaskDescriptionByID(id : String) : Future[Option[TaskDescription]] = {
    val query = BSONDocument("_id" -> BSONDocument("$regex" -> id))
    val cursor : Future[Cursor[TaskDescription]] = taskCollection.map(f => f.find(query).cursor[TaskDescription]())
    val futurTask : Future[Option[TaskDescription]] = cursor.flatMap(_.headOption)
    futurTask
  }

  def findTaskDescriptionByDescription(description: String, page : Int , pageSize : Int) : Future[List[TaskDescription]] = {
    val query = Json.obj{
      "description" -> Json.obj{"$regex" -> description}
    }

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findTaskDescriptionByStartDate(startDate : ZonedDateTime, page : Int, pageSize : Int) : Future[List[TaskDescription]] = {
    val query = Json.obj{
      "startDate" -> startDate
    }

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findTaskDescriptionByCategory(category : String, page : Int, pageSize : Int): Future[List[TaskDescription]] ={
    val query = Json.obj{
      "category" -> category
    }

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findTaskDescriptionByType(typeTask : String, page : Int, pageSize : Int) : Future[List[TaskDescription]] = {
    val query = Json.obj{
      "type" -> typeTask.toUpperCase
    }

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findSinglePersonTaskById(id: String): Future[Option[SinglePersonTask]] = {
    val query = BSONDocument("_id" -> id , "type" -> "SINGLE")
    val cursor : Future[Cursor[SinglePersonTask]] = taskCollection.map(f => f.find(query).cursor[SinglePersonTask]())
    val futurTask : Future[Option[SinglePersonTask]] = cursor.flatMap(_.headOption)
    futurTask
  }

  def findGroupedPersonTaskById(id: String): Future[Option[GroupedTask]] = {
    val query = BSONDocument("_id" -> id , "type" -> "GROUPED")
    val cursor : Future[Cursor[GroupedTask]] = taskCollection.map(f => f.find(query).cursor[GroupedTask]())
    val futurTask : Future[Option[GroupedTask]] = cursor.flatMap(_.headOption)
    futurTask
  }

  def findDetailOfTaskDecription(taskDescription: TaskDescription) = {
    if(taskDescription.`type` == TaskType.SINGLE){
      findSinglePersonTaskById(taskDescription._id)
    }else{
      findGroupedPersonTaskById(taskDescription._id)
    }
  }

  def addSinglePersonTask(task : SinglePersonTask) = {
    val jsonDoc = SinglePersonTask.fmt.writes(task)
    taskCollection.map(c => c.insert(jsonDoc))
  }

  def addGroupedTask(task : GroupedTask) = {
    val jsonDoc = GroupedTask.fmt.writes(task)
    taskCollection.map(c => c.insert(jsonDoc))
  }

  def updateSinglePersonTask(id : String, task: SinglePersonTask ) = {
    val selectUpdate = Json.obj("_id" -> id)
    val updateQuery = Json.obj("description" -> task.description,
                               "startDate" -> Json.obj("$date" -> task.startDate.toInstant.toEpochMilli),
                               "endDate" -> Json.obj("$date" -> task.endDate.toInstant.toEpochMilli),
                               "status" -> task.status,
                               "employeeId" -> task.employeeId,
                               "category" -> task.category,
                               "alert" -> task.alert,
                               "type"-> TaskType.SINGLE
    )
    taskCollection.map(c => c.update(selectUpdate,updateQuery))
  }

  def updateGroupedTask(id : String, task: GroupedTask ) = {
    val selectUpdate = Json.obj("_id" -> id)
    val updateQuery = Json.obj("description" -> task.description,
                               "startDate" -> Json.obj("$date" -> task.startDate.toInstant.toEpochMilli),
                               "endDate" -> Json.obj("$date" -> task.endDate.toInstant.toEpochMilli),
                               "status" -> task.status,
                               "groupName" -> task.groupName,
                               "category" -> task.category,
                               "alert" -> task.alert,
                               "type"-> TaskType.GROUPED
    )
    taskCollection.map(c => c.update(selectUpdate,updateQuery))
  }

  def deleteTask(id : String) = {
    val removeQuery = Json.obj("_id" -> id)
    taskCollection.map(c => c.remove(removeQuery))
  }

  def removeTaskCategoryFromTask(taskCategoryName : String) = {
    val selectUpdate = Json.obj()
    val updateQuery = Json.obj("$pull" -> Json.obj("category" -> taskCategoryName))
    taskCollection.map(c => c.update(selectUpdate,updateQuery,multi = true))
  }

  def removeUserGroupFromTask(userGroupName : String) = {
    val selectUpdate = Json.obj()
    val updateQuery = Json.obj("$pull" -> Json.obj("groupName" -> userGroupName))
    taskCollection.map(c => c.update(selectUpdate,updateQuery,multi = true))
  }
}
