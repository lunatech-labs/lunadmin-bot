package store

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import models._
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaskDataStore @Inject()(val reactiveMongoApi: ReactiveMongoApi, conf: Configuration)(implicit ec: ExecutionContext) {

  private val taskCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("task"))

  import reactivemongo.play.json.ImplicitBSONHandlers._

  def findAllTaskDescription(page: Int, pageSize: Int): Future[List[TaskDescription]] = {
    val query = BSONDocument("isActive" -> true)
    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  private def findAllDescriptionForCount(): Future[List[TaskDescription]] = {
    val query = BSONDocument("isActive" -> true)
    taskCollection.flatMap(
      _.find(query)
        .cursor[TaskDescription]()
        .collect[List](-1, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findNumberOfPage(pageSize: Int): Future[Int] = {
    findAllDescriptionForCount().map(e =>
      Math.ceil(e.size / pageSize.toDouble).toInt)
  }

  private def findAllTaskOfAUserForCount(idOfUser: String, groupName: List[String]): Future[List[TaskDescription]] = {
    val query = Json.obj(
      "isActive" -> true,
      "$or" -> Json.arr(
        Json.obj("employeeId" -> idOfUser),
        Json.obj("groupName" -> Json.obj("$in" -> groupName))
      )
    )

    taskCollection.flatMap(
      _.find(query)
        .cursor[TaskDescription]()
        .collect[List](-1, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findNumberOfPageOfAUser(idOfUser: String, groupName: List[String], pageSize: Int): Future[Int] = {
    findAllTaskOfAUserForCount(idOfUser, groupName).map(e => Math.round(e.size / pageSize))
  }

  def findTaskDescriptionByID(id: String): Future[Option[TaskDescription]] = {
    val query = BSONDocument("isActive" -> true, "_id" -> BSONDocument("$regex" -> id))
    val cursor: Future[Cursor[TaskDescription]] = taskCollection.map(f => f.find(query).cursor[TaskDescription]())
    val futurTask: Future[Option[TaskDescription]] = cursor.flatMap(_.headOption)
    futurTask
  }

  def findTaskDescriptionByDescription(description: String, page: Int, pageSize: Int): Future[List[TaskDescription]] = {
    val query = Json.obj(
      "isActive" -> true,
      "description" -> Json.obj {
        "$regex" -> description
      }
    )

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findTaskOfAUser(idOfUser: String, groupName: List[String], page: Int, pageSize: Int): Future[List[TaskDescription]] = {
    val query = Json.obj(
      "isActive" -> true,
      "$or" -> Json.arr(
        Json.obj("employeeId" -> idOfUser),
        Json.obj("groupName" -> Json.obj("$in" -> groupName))
      )
    )

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  private def findSingleTaskIdsForDelete(idOfUser: String): Future[List[(String, String)]] = {
    val query = Json.obj("isActive" -> true, "type" -> "SINGLE", "employeeId" -> idOfUser)

    taskCollection.flatMap(
      _.find(query)
        .cursor[TaskDescription]()
        .collect[List](-1, Cursor.FailOnError[List[TaskDescription]]())
        .map(list => list.map(taskdescription => (taskdescription._id, taskdescription.`type`.toString)))
    )
  }

  def deleteAllSingleTaskOfUser(idOfUser: String): Unit = {
    findSingleTaskIdsForDelete(idOfUser).map { e =>
      e.foreach { id => setTaskToInactive(id._1) }
    }
  }

  def findTaskDescriptionByStartDate(startDate: ZonedDateTime, page: Int, pageSize: Int): Future[List[TaskDescription]] = {
    val query = Json.obj(
      "isActive" -> true,
      "startDate" -> startDate
    )

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findTaskDescriptionByCategory(category: String, page: Int, pageSize: Int): Future[List[TaskDescription]] = {
    val query = Json.obj(
      "isActive" -> true,
      "category" -> category
    )

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findAllActiveSingleTask() : Future[List[SinglePersonTask]] = {
    val query = Json.obj(
      "isActive" -> true,
      "type" -> "SINGLE"
    )

    taskCollection.flatMap(
      _.find(query)
        .cursor[SinglePersonTask]()
        .collect[List](-1, Cursor.FailOnError[List[SinglePersonTask]]())
    )
  }

  def findAllActiveGroupedTask() : Future[List[GroupedTask]] = {
    val query = Json.obj(
      "isActive" -> true,
      "type" -> "GROUPED"
    )

    taskCollection.flatMap(
      _.find(query)
        .cursor[GroupedTask]()
        .collect[List](-1, Cursor.FailOnError[List[GroupedTask]]())
    )
  }

  def setTaskToInactive(idOfTask : String) : Unit = {
    val selectQuery = Json.obj(
      "_id" -> idOfTask
    )
    val updateQuery = Json.obj(
      "$set" -> Json.obj(
        "isActive" -> false
      )
    )

    taskCollection.map(c => c.update(selectQuery,updateQuery))
  }

  def findTaskDescriptionByType(typeTask: String, page: Int, pageSize: Int): Future[List[TaskDescription]] = {
    val query = Json.obj(
      "isActive" -> true,
      "type" -> typeTask.toUpperCase
    )

    taskCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[TaskDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[TaskDescription]]())
    )
  }

  def findSinglePersonTaskById(id: String): Future[Option[SinglePersonTask]] = {
    val query = BSONDocument("isActive" -> true, "_id" -> id, "type" -> "SINGLE")
    val cursor: Future[Cursor[SinglePersonTask]] = taskCollection.map(f => f.find(query).cursor[SinglePersonTask]())
    val futurTask: Future[Option[SinglePersonTask]] = cursor.flatMap(_.headOption)
    futurTask
  }

  def findGroupedPersonTaskById(id: String): Future[Option[GroupedTask]] = {
    val query = BSONDocument("isActive" -> true, "_id" -> id, "type" -> "GROUPED")
    val cursor: Future[Cursor[GroupedTask]] = taskCollection.map(f => f.find(query).cursor[GroupedTask]())
    val futurTask: Future[Option[GroupedTask]] = cursor.flatMap(_.headOption)
    futurTask
  }

  def findDetailOfTaskDecription(taskDescription: TaskDescription) = {
    if (taskDescription.`type` == TaskType.SINGLE) {
      findSinglePersonTaskById(taskDescription._id)
    } else {
      findGroupedPersonTaskById(taskDescription._id)
    }
  }

  def addSinglePersonTask(task: SinglePersonTask): Unit = {
    val jsonDoc = SinglePersonTask.fmt.writes(task)
    taskCollection.map(c => c.insert(jsonDoc))
  }

  def addGroupedTask(task: GroupedTask): Unit = {
    val jsonDoc = GroupedTask.fmt.writes(task)
    taskCollection.map(c => c.insert(jsonDoc))
  }

  def updateSinglePersonTask(id: String, task: SinglePersonTask): Unit = {
    val selectUpdate = Json.obj("_id" -> id)
    val updateQuery = Json.obj("description" -> task.description,
      "startDate" -> Json.obj("$date" -> task.startDate.toInstant.toEpochMilli),
      "endDate" -> Json.obj("$date" -> task.endDate.toInstant.toEpochMilli),
      "status" -> task.status,
      "employeeId" -> task.employeeId,
      "category" -> task.category,
      "alert" -> task.alert,
      "type" -> TaskType.SINGLE,
      "isActive" -> task.isActive
    )
    taskCollection.map(c => c.update(selectUpdate, updateQuery))
  }

  def updateGroupedTask(id: String, task: GroupedTask): Unit = {
    val selectUpdate = Json.obj("_id" -> id)
    val updateQuery = Json.obj("description" -> task.description,
      "startDate" -> Json.obj("$date" -> task.startDate.toInstant.toEpochMilli),
      "endDate" -> Json.obj("$date" -> task.endDate.toInstant.toEpochMilli),
      "status" -> task.status,
      "groupName" -> task.groupName,
      "category" -> task.category,
      "alert" -> task.alert,
      "type" -> TaskType.GROUPED,
      "isActive" -> task.isActive
    )
    taskCollection.map(c => c.update(selectUpdate, updateQuery))
  }

  def removeTaskCategoryFromTask(taskCategoryName: String): Unit = {
    val selectUpdate = Json.obj()
    val updateQuery = Json.obj("$pull" -> Json.obj("category" -> taskCategoryName))
    taskCollection.map(c => c.update(selectUpdate, updateQuery, multi = true))
  }

  def removeUserGroupFromTask(userGroupName: String): Unit = {
    val selectUpdate = Json.obj()
    val updateQuery = Json.obj("$pull" -> Json.obj("groupName" -> userGroupName))
    taskCollection.map(c => c.update(selectUpdate, updateQuery, multi = true))
  }

  def replaceUserGroupFromTask(oldName: String, newName: String) = {
    val selectQuery = Json.obj("groupName" -> oldName)
    val addQuery = Json.obj("$push" -> Json.obj("groupName" -> newName))
    val removeQuery = Json.obj("$pull" -> Json.obj("groupName" -> oldName))

    taskCollection.map { c =>
      for {
        a <- c.update(selectQuery, addQuery, multi = true)
        b <- c.update(selectQuery, removeQuery, multi = true)
      } yield (a, b)
    }
  }
}
