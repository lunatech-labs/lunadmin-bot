package store

import java.time.ZonedDateTime
import java.util.Date

import controllers.Starter
import javax.inject.Inject
import models.TaskType.TaskType
import models._
import play.api.{Configuration, Logger}
import play.api.http.MediaType.parse
import play.api.libs.json.{JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.{BSONDocument, BSONObjectID, BSONRegex}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.matching.Regex


class TaskDataStore @Inject()(val reactiveMongoApi: ReactiveMongoApi ,conf :Configuration)(implicit ec: ExecutionContext) {

  private val taskCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("task"))
  import reactivemongo.play.json.ImplicitBSONHandlers._

  //val cursor : Future[Cursor[TaskCategory]] = taskCollection.map(f => f.find().cursor[TaskCategory]())
  //val futurList : Future[List[TaskCategory]] = cursor.flatMap(_.collect[List]())

//  def findAll() : Future[List[Task]] = {
//    val querySinglePersonTask = BSONDocument("type" -> "SINGLE")
//    val cursorSinglePersonTask : Future[Cursor[SinglePersonTask]] = taskCollection.map(f => f.find(querySinglePersonTask).cursor[SinglePersonTask]())
//    val futurListSinglePersonTask : Future[List[SinglePersonTask]] = cursorSinglePersonTask.flatMap(_.collect[List](-1,Cursor.FailOnError[List[SinglePersonTask]]()))
//
//    val queryGroupedPersonTask = BSONDocument("type" -> "GROUPED")
//    val cursorGroupedPersonTask : Future[Cursor[GroupedTask]] = taskCollection.map(f => f.find(queryGroupedPersonTask).cursor[GroupedTask]())
//    val futurListGroupedPersonTask : Future[List[GroupedTask]] = cursorGroupedPersonTask.flatMap(_.collect[List](-1,Cursor.FailOnError[List[GroupedTask]]()))
//
//    for {
//      singles <- futurListSinglePersonTask
//      grouped <- futurListGroupedPersonTask
//    } yield singles ++ grouped
//  }

  def findAllTaskDescription(page : Int, pageSize : Int) : Future[List[TaskDescription]] = {
    val query = BSONDocument()
    taskCollection.flatMap(
      _.find(query)
      .options(QueryOpts(skipN = page * pageSize, pageSize))
      .cursor[TaskDescription]()
      .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findTaskDescriptionByDescription(description: String, page : Int , pageSize : Int) : Future[List[TaskDescription]] = {
    val query = Json.obj{
      "description" -> Json.obj{"$regex" -> description}
    }
    Logger.info(new Regex(description).regex)
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
                               "startDate" -> task.startDate,
                               "endDate" -> task.endDate,
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
                               "startDate" -> task.startDate,
                               "endDate" -> task.endDate,
                               "status" -> task.status,
                               "group" -> task.group,
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

}
