package store

import javax.inject.{Inject, Singleton}
import models._
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Json, _}

@Singleton
class UserDataStore @Inject()(val reactiveMongoApi: ReactiveMongoApi, taskDataStore: TaskDataStore, conf: Configuration)(implicit ec: ExecutionContext) {
  private val userCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("user"))

  import reactivemongo.play.json.ImplicitBSONHandlers._


  def findUserById(id: String): Future[Option[User]] = {
    val query = Json.obj("_id" -> id)
    val cursor: Future[Cursor[User]] = userCollection.map(f => f.find(query).cursor[User]())
    val futurUser: Future[Option[User]] = cursor.flatMap(_.headOption)
    futurUser
  }

  def findEveryActiveUser(): Future[List[User]] = {
    val query = BSONDocument("isActive" -> true)
    userCollection.flatMap(
      _.find(query)
        .cursor[User]()
        .collect[List](-1, Cursor.FailOnError[List[User]]())
    )
  }

  def findUsersByUserGroup(userGroupName: String): Future[List[User]] = {
    val query = Json.obj("isActive" -> true, "groupName" -> userGroupName)

    userCollection.flatMap(
      _.find(query)
        .cursor[User]()
        .collect[List](-1, Cursor.FailOnError[List[User]]())
    )
  }

  def findAllUserDescription(page: Int, pageSize: Int): Future[List[UserDescription]] = {
    val query = BSONDocument("isActive" -> true)
    userCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[UserDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findEveryUserDescription(): Future[List[UserDescription]] = {
    val query = BSONDocument("isActive" -> true)
    userCollection.flatMap(
      _.find(query)
        .cursor[UserDescription]()
        .collect[List](-1, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findUserDescriptionByName(nameToLookFor: String, page: Int, pageSize: Int): Future[List[UserDescription]] = {
    val query = Json.obj(
      "isActive" -> true,
      "$or" -> Json.arr(
        Json.obj("firstName" -> Json.obj("$regex" -> nameToLookFor)),
        Json.obj("lastName" -> Json.obj("$regex" -> nameToLookFor))
      )
    )

    userCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[UserDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findUserDescriptionByMail(mailToLookFor: String, page: Int, pageSize: Int): Future[List[UserDescription]] = {
    val query = Json.obj("isActive" -> true, "mail" -> Json.obj("$regex" -> mailToLookFor))

    userCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[UserDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findNumberOfPage(pageSize: Int): Future[Int] = {
    findEveryUserDescription().map{e =>
      Math.ceil(e.size / pageSize.toDouble).toInt}
  }

  def findUserDescriptionByUserGroup(userGroupName: String): Future[List[UserDescription]] = {
    val query = Json.obj("isActive" -> true, "groupName" -> userGroupName)

    userCollection.flatMap(
      _.find(query)
        .cursor[UserDescription]()
        .collect[List](-1, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findUserDescriptionByListOfUserGroup(listOfUserName: List[String]): Future[List[UserDescription]] = {
    val query = Json.obj("isActive" -> true,
      "groupName" -> Json.obj(
        "$in" -> listOfUserName))

    userCollection.flatMap(
      _.find(query)
        .cursor[UserDescription]()
        .collect[List](-1, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findUserByMail(mail: String): Future[Option[User]] = {
    val query = Json.obj("isActive" -> true, "mail" -> mail)
    userCollection.flatMap(
      _.find(query)
        .cursor[User]()
        .headOption
    )
  }

  def addUser(user: User) = {
    val javaDoc = Json.obj(
      "_id" -> user._id,
      "mail" -> user.mail,
      "password" -> user.password,
      "lastName" -> user.lastName,
      "firstName" -> user.firstName,
      "birthDate" -> user.birthDate.map(User.dateFormatter.writes),
      "groupName" -> user.groupName,
      "status" -> user.status,
      "hireDate" -> user.hireDate.map(User.dateFormatter.writes),
      "picture" -> user.picture,
      "phone" -> user.phone,
      "cloudPaths" -> user.cloudPaths,
      "isActive" -> user.isActive,
      "timeZone" -> user.timeZone
    )
    userCollection.map(c => c.insert(javaDoc))
  }

  def updateUser(id: String, user: User) = {
    val selectUpdate = Json.obj("_id" -> id)
    val updateQuery = Json.obj("mail" -> user.mail,
      "password" -> user.password,
      "lastName" -> user.lastName,
      "firstName" -> user.firstName,
      "birthDate" -> user.birthDate.map(User.dateFormatter.writes),
      "groupName" -> user.groupName,
      "status" -> user.status,
      "hireDate" -> user.hireDate.map(User.dateFormatter.writes),
      "picture" -> user.picture,
      "phone" -> user.phone,
      "cloudPaths" -> user.cloudPaths,
      "isActive" -> user.isActive,
      "timeZone" -> user.timeZone
    )
    userCollection.map(c => c.update(selectUpdate, updateQuery))
  }

  def deleteUser(idOfUser: String) = {
    val user = findUserById(idOfUser)
    user.map { u =>
      if (u.isDefined) {
        updateUser(idOfUser, User(
          mail = u.get.mail,
          password = u.get.password,
          lastName = u.get.lastName,
          firstName = u.get.firstName,
          birthDate = u.get.birthDate,
          groupName = u.get.groupName,
          status = u.get.status,
          hireDate = u.get.hireDate,
          picture = u.get.picture,
          phone = u.get.phone,
          cloudPaths = u.get.cloudPaths,
          isActive = false,
          timeZone = u.get.timeZone
        ))
        taskDataStore.deleteAllSingleTaskOfUser(idOfUser)
      }
    }
  }

  def findEveryExistingMailToCheckForRegistering(idOfUser: Option[String] = None): Future[List[String]] = {
    findUserById(idOfUser.getOrElse("none")).flatMap { optUser =>
      optUser.map { user =>
        val query = Json.obj("mail" -> Json.obj("$ne" -> user.mail))
        val projection = Json.obj("mail" -> 1)
        userCollection.flatMap(
          _.find(query, projection)
            .cursor[mailTemplate]()
            .collect[List](-1, Cursor.FailOnError[List[mailTemplate]]())
        ).map(l => l.map(e => e.mail))
      }.getOrElse {
        val query = Json.obj()
        val projection = Json.obj("mail" -> 1)
        userCollection.flatMap(
          _.find(query, projection)
            .cursor[mailTemplate]()
            .collect[List](-1, Cursor.FailOnError[List[mailTemplate]]())
        ).map(l => l.map(e => e.mail))
      }
    }
  }

  def checkIfSingleTaskIsAssignedToUser(idOfUser: String, task: SinglePersonTask): Boolean = {
    if (task.employeeId == idOfUser)
      true
    else {
      false
    }
  }

  def checkIfUserIsPartOfGroupedTask(groupName: List[String], task: GroupedTask): Boolean = {
    if (groupName.intersect(task.groupName).nonEmpty) {
      true
    } else {
      false
    }
  }

  def removeUsersGroupFromUser(userGroupName: String) = {
    val selectUpdate = Json.obj()
    val updateQuery = Json.obj("$pull" -> Json.obj("groupName" -> userGroupName))
    userCollection.map(c => c.update(selectUpdate, updateQuery, multi = true))
  }

  def addUserToUserGroup(user: User, userGroupName: String) = {
    updateUser(user._id, User(
      mail = user.mail,
      password = user.password,
      firstName = user.firstName,
      lastName = user.lastName,
      birthDate = user.birthDate,
      status = user.status,
      hireDate = user.hireDate,
      picture = user.picture,
      phone = user.phone,
      cloudPaths = user.cloudPaths,
      isActive = user.isActive,
      timeZone = user.timeZone,
      groupName = user.groupName.map(l => Some(l :+ userGroupName)).getOrElse(Some(List(userGroupName)))
    ))
  }

  def removeUserFromUserGroup(idOfUser: String, userGroupName: String) = {
    val selectUpdate = Json.obj("_id" -> idOfUser)
    val updateQuery = Json.obj("$pull" -> Json.obj("groupName" -> userGroupName))
    userCollection.map(c => c.update(selectUpdate, updateQuery))
  }

  def replaceUserGroupFromUser(oldName: String, newName: String) = {
    val selectQuery = Json.obj("groupName" -> oldName)
    val addQuery = Json.obj("$push" -> Json.obj("groupName" -> newName))
    val removeQuery = Json.obj("$pull" -> Json.obj("groupName" -> oldName))

    userCollection.map { c =>
      for {
        a <- c.update(selectQuery, addQuery, multi = true)
        b <- c.update(selectQuery, removeQuery, multi = true)
      } yield (a, b)
    }
  }
}